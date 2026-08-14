/**
 * 빌더의 순수 로직 — 명령 생성과 설정 점검.
 *
 * DOM 을 만지지 않는다. 그래서 브라우저 없이 Node 로 검증할 수 있다
 * (k6/tools/test-builder.mjs). GUI 는 화면에서 값을 모아 여기 넘기는 일만 한다.
 *
 * 이 분리가 필요한 이유
 *   빌더의 실제 산출물은 "복사해서 실행할 명령 한 줄"이다. 그게 틀리면 부하 테스트가
 *   엉뚱한 설정으로 돌거나 아예 안 돈다. 특히 따옴표 처리는 눈으로 검토하기 어렵고
 *   틀렸을 때 조용히 다른 값으로 실행되기도 한다 — 그래서 테스트가 붙어야 한다.
 *
 * 일반 스크립트로 쓴다(모듈 아님). 빌더 HTML 이 file:// 로 열려야 해서다.
 */
(function (root) {
  'use strict';

  /**
   * 셸에 맞게 값을 감싼다.
   *
   * 두 셸 모두 작은따옴표 안의 내용을 그대로 통과시키지만, **값 안에 작은따옴표가 있을 때
   * 이스케이프 방법이 다르다.** 비밀번호에 따옴표가 있으면 이걸 틀리는 순간 명령이 깨지거나,
   * 더 나쁘게는 다른 값으로 조용히 실행된다.
   *
   *   bash        ' -> '\''  (닫고, 이스케이프한 따옴표를 붙이고, 다시 연다)
   *   PowerShell  ' -> ''    (따옴표 두 개가 따옴표 한 개를 뜻한다)
   */
  function quote(value, shell) {
    var text = String(value);
    if (shell === 'ps') return "'" + text.replace(/'/g, "''") + "'";
    return "'" + text.replace(/'/g, "'\\''") + "'";
  }

  /** 기본값. 이 값과 같으면 명령에 넣지 않는다 — 명령이 길어지면 읽기 어렵다. */
  var THRESHOLD_DEFAULTS = {
    p95Public: '500',
    p95Read: '1000',
    p95Write: '1500',
    p95Ai: '30000',
    maxFail: '0.01',
  };
  var THRESHOLD_ENV = {
    p95Public: 'P95_PUBLIC',
    p95Read: 'P95_READ',
    p95Write: 'P95_WRITE',
    p95Ai: 'P95_AI',
    maxFail: 'MAX_FAIL_RATE',
  };

  function chosenOf(cfg, catalog) {
    var picked = {};
    (cfg.apis || []).forEach(function (key) {
      picked[key] = true;
    });
    return catalog.filter(function (e) {
      return picked[e.key];
    });
  }

  /**
   * 고른 API 를 카탈로그 순서로 정렬해 문자열로 만든다.
   *
   * 순서를 카탈로그에 맞추는 이유: 체크한 순서를 그대로 쓰면 실행마다 호출 순서가 달라져서
   * 개선 전/후 비교에 잡음이 섞인다. 같은 선택은 항상 같은 순서여야 한다.
   */
  function apiList(cfg, catalog) {
    var picked = {};
    (cfg.apis || []).forEach(function (key) {
      picked[key] = true;
    });
    var ordered = catalog
      .filter(function (e) {
        return picked[e.key];
      })
      .map(function (e) {
        return e.key;
      });
    return ordered.concat(cfg.extraApis || []).join(',');
  }

  /** k6 에 넘길 -e 값들. [[이름, 값], ...] */
  function envPairs(cfg, catalog) {
    var chosen = chosenOf(cfg, catalog);
    var accounts = (cfg.accounts || []).filter(function (a) {
      return a.email && a.password;
    });

    var pairs = [
      ['BASE_URL', cfg.baseUrl],
      [
        'ACCOUNTS',
        accounts
          .map(function (a) {
            return a.email + ':' + a.password + ':' + a.role;
          })
          .join(','),
      ],
      ['APIS', apiList(cfg, catalog)],
      ['PROFILE', cfg.profile],
      ['VUS', cfg.vus],
      ['DURATION', cfg.duration],
      ['RAMP', cfg.ramp],
      ['THINK_MS', cfg.thinkMs],
      ['TESTID', cfg.testid],
    ];

    var usesAi = chosen.some(function (e) {
      return e.auth === 'internal';
    });
    if (usesAi) {
      pairs.push(['AI_BASE_URL', cfg.aiBaseUrl]);
      pairs.push(['INTERNAL_API_KEY', cfg.internalKey]);
    }

    if (cfg.ws) {
      pairs.push(['WS', '1']);
      pairs.push(['WS_VUS', cfg.wsVus]);
      if (cfg.wsHold) pairs.push(['WS_HOLD', cfg.wsHold]);
    }

    Object.keys(THRESHOLD_DEFAULTS).forEach(function (id) {
      var value = cfg[id];
      if (value !== undefined && value !== '' && String(value) !== THRESHOLD_DEFAULTS[id]) {
        pairs.push([THRESHOLD_ENV[id], value]);
      }
    });

    // THINK_MS 는 0 이 의미 있는 값이라 빈 값만 걸러낸다.
    return pairs.filter(function (p) {
      return p[1] !== '' && p[1] !== undefined && p[1] !== null;
    });
  }

  /**
   * 실행 명령 한 줄.
   *
   * 줄바꿈 이어쓰기(PowerShell 백틱 / bash 역슬래시)를 쓰지 않는다. 복사·붙여넣기 과정에서
   * 자주 깨지고, 깨졌을 때 증상이 "일부 설정이 빠진 채 실행"이라 알아채기 어렵다.
   */
  function buildCommand(cfg, catalog, shell) {
    var args = envPairs(cfg, catalog)
      .map(function (pair) {
        return '-e ' + pair[0] + '=' + quote(pair[1], shell);
      })
      .join(' ');
    return 'k6 run k6/main.js ' + args;
  }

  /** 설정 점검. [[종류, 문구], ...] 종류는 bad | warn | info | ok */
  function checks(cfg, catalog) {
    var out = [];
    var chosen = chosenOf(cfg, catalog);
    var accounts = (cfg.accounts || []).filter(function (a) {
      return a.email && a.password;
    });
    var vus = Number(cfg.vus || 0);

    if (!chosen.length && !(cfg.extraApis || []).length) {
      out.push(['bad', 'API 를 하나도 고르지 않았다. 4번에서 고른다.']);
      return out;
    }

    var needsLogin = chosen.some(function (e) {
      return e.auth !== 'none' && e.auth !== 'internal';
    });
    if (needsLogin && !accounts.length) {
      out.push(['bad', '로그인이 필요한 API 를 골랐는데 계정이 없다. 2번에 계정을 넣는다.']);
    }

    ['CLIENT', 'FREELANCER'].forEach(function (role) {
      var wants = chosen.filter(function (e) {
        return e.auth === role;
      });
      var has = accounts.some(function (a) {
        return a.role === role;
      });
      if (wants.length && !has) {
        var names = wants.slice(0, 3).map(function (e) {
          return e.key;
        });
        out.push([
          'bad',
          role +
            ' 전용 API ' +
            wants.length +
            '개를 골랐는데 ' +
            role +
            ' 계정이 없다. 그 API 들은 실행에서 제외된다 (' +
            names.join(', ') +
            (wants.length > 3 ? ' …' : '') +
            ')',
        ]);
      }
    });

    var ai = chosen.filter(function (e) {
      return e.auth === 'internal';
    });
    if (ai.length) {
      if (!String(cfg.internalKey || '').trim()) {
        out.push(['bad', 'AI API 를 골랐는데 내부 API 키가 비어 있다. 1번에 넣는다.']);
      }
      out.push([
        'info',
        'AI 는 파이썬에 직접 붙는다. 파이썬을 반드시 AI_STUB_MODE=true 로 띄운다 — 아니면 ' +
          '실제 Gemini 쿼터가 소진되고 키가 전부 쿨다운에 들어가 그 시점부터 전부 실패한다. ' +
          'k6 가 시작할 때 확인하고, 스텁이 아니면 멈춘다.',
      ]);
    }

    var writes = chosen.filter(function (e) {
      return e.group === 'write';
    });
    if (writes.length) {
      out.push([
        'warn',
        '쓰기 API ' +
          writes.length +
          '개를 골랐다. 데이터가 실제로 남는다(메시지·읽음 상태). 운영 환경에는 돌리지 않는다.',
      ]);
    }

    if (needsLogin && accounts.length && cfg.profile !== 'smoke') {
      // VU 10개당 계정 1개, 최대 20개까지만 권한다.
      //
      // 비율만 따지면 VU 200 에 계정 40개가 되는데, 테스트 계정을 40개 만드는 건 현실적이지
      // 않아서 경고가 늘 켜져 있게 된다. 늘 켜져 있는 경고는 아무도 안 읽는다.
      // 20개면 계정당 VU 10개라 계정 단위 캐시·락의 영향을 판별하기에 충분하다.
      var want = Math.min(20, Math.ceil(vus / 10));
      if (accounts.length < want) {
        out.push([
          'warn',
          '계정 ' +
            accounts.length +
            '개로 VU ' +
            vus +
            ' 를 돌린다. ' +
            want +
            '개 이상을 권장한다 — 여러 VU 가 같은 토큰과 같은 사용자 데이터를 공유하면 ' +
            '계정 단위 캐시나 락 때문에 실제와 다른 숫자가 나온다.',
        ]);
      }
    }

    if (cfg.profile === 'smoke') {
      out.push(['info', 'smoke 는 VU 1 · 30초 고정이다. VU 와 유지 시간 설정은 무시된다.']);
    }
    if (cfg.profile === 'stress') {
      out.push([
        'info',
        'stress 는 목표 VU 의 25% → 50% → 75% → 100% → 150% 로 계단식 상승한다. ' +
          '총 실행 시간은 (증감 + 유지) × 5 + 증감 이다.',
      ]);
    }
    if (Number(cfg.thinkMs) === 0) {
      out.push([
        'warn',
        '생각 시간이 0 이다. VU 가 쉬지 않고 요청해서 서버 한계보다 내 PC(k6) 한계에 먼저 ' +
          '닿을 수 있다. CPU 사용률을 함께 보고, k6 쪽이 포화면 값을 올린다.',
      ]);
    }

    var idNeeded = chosen.filter(function (e) {
      return (e.needs || []).length;
    });
    if (idNeeded.length) {
      out.push([
        'info',
        'ID 가 필요한 API ' +
          idNeeded.length +
          '개를 골랐다. 실행 시작 시 목록 API 로 실제 ID 를 찾는다. 해당 데이터가 없으면 ' +
          '그 API 는 제외되고 리포트에 남는다.',
      ]);
    }

    var hasBad = out.some(function (m) {
      return m[0] === 'bad';
    });
    if (!hasBad) {
      out.unshift(['ok', '설정에 막는 문제가 없다. 아래 명령을 복사해 실행한다.']);
    }
    return out;
  }

  root.K6Logic = {
    quote: quote,
    apiList: apiList,
    envPairs: envPairs,
    buildCommand: buildCommand,
    checks: checks,
    THRESHOLD_DEFAULTS: THRESHOLD_DEFAULTS,
  };

  // Node 에서 require 로 불러 테스트할 수 있게 한다.
  if (typeof module !== 'undefined' && module.exports) module.exports = root.K6Logic;
})(typeof window !== 'undefined' ? window : globalThis);
