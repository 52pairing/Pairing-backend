/**
 * Pairing 부하 테스트 — 진입점.
 *
 * 실행은 builder/index.html 에서 명령을 만들어 복사하거나, run.ps1 을 쓴다.
 * 직접 돌릴 때:
 *
 *   k6 run k6/main.js \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e ACCOUNTS='a@b.com:pw:FREELANCER,c@d.com:pw:CLIENT' \
 *     -e APIS=me,notifs,chatrooms \
 *     -e PROFILE=load -e VUS=20 -e DURATION=3m -e TESTID=before
 *
 * ---------------------------------------------------------------------------
 * 설계에서 중요한 세 가지
 *
 * 1) 로그인은 setup() 에서 계정당 한 번뿐이다.
 *    이 서비스는 로그인 시 그 계정의 이전 세션을 끊는다. VU 루프 안에서 로그인하면
 *    서로의 토큰을 죽여서 401 폭풍이 난다. (lib/auth.js 의 설명 참고)
 *
 * 2) 엔드포인트마다 별도 Trend 를 만든다.
 *    k6 요약에는 태그별 하위 통계가 안 들어간다. "어느 API 가 느린가"를 리포트에 남기려면
 *    엔드포인트별 지표를 직접 만들어야 한다.
 *
 * 3) AI 는 전체 지연 기준에서 뺀다.
 *    AI 호출은 초 단위고 조회는 밀리초 단위다. 한 기준으로 묶으면 AI 하나가 전체 p95 를
 *    끌어올려서 조회 성능이 나빠져도 그래프에 안 보인다. 그래서 그룹별로 기준을 나눈다.
 */

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

import { ENDPOINTS, findEndpoint, resolvePath } from './endpoints.js';
import { parseAccounts, loginAll, pickAccount, bearerHeaders, cookieHeaders } from './lib/auth.js';
import { discoverIds } from './lib/discover.js';
import { textSummary, htmlReport } from './lib/report.js';
import {
  connectFrame,
  subscribeFrame,
  disconnectFrame,
  commandOf,
  destinationsFor,
  websocketUrl,
  HEARTBEAT_MS,
} from './lib/stomp.js';

// ---------------------------------------------------------------------------
// 설정
// ---------------------------------------------------------------------------

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const AI_BASE_URL = (__ENV.AI_BASE_URL || 'http://localhost:8000').replace(/\/+$/, '');
const INTERNAL_API_KEY = __ENV.INTERNAL_API_KEY || '';

const PROFILE = __ENV.PROFILE || 'smoke';
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '1m';
const RAMP = __ENV.RAMP || '30s';
const TESTID = __ENV.TESTID || 'run';
const THINK_MS = Number(__ENV.THINK_MS || 500);

const WS_ENABLED = __ENV.WS === '1' || __ENV.WS === 'true';
const WS_VUS = Number(__ENV.WS_VUS || 5);
const WS_HOLD = __ENV.WS_HOLD || DURATION;

// 그룹별 지연 기준(ms). 넘으면 k6 가 종료 코드 99 로 끝난다.
const P95_PUBLIC = Number(__ENV.P95_PUBLIC || 500);
const P95_READ = Number(__ENV.P95_READ || 1000);
const P95_WRITE = Number(__ENV.P95_WRITE || 1500);
const P95_AI = Number(__ENV.P95_AI || 30000);
const MAX_FAIL_RATE = Number(__ENV.MAX_FAIL_RATE || 0.01);

/** 고른 API 목록. 비어 있으면 로그인 없이 되는 공개 API 만 돈다. */
const SELECTED = String(__ENV.APIS || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

const SELECTED_ENDPOINTS = (SELECTED.length ? SELECTED : defaultSelection()).map(findEndpoint);

function defaultSelection() {
  return ENDPOINTS.filter((e) => e.group === 'public').map((e) => e.key);
}

// ---------------------------------------------------------------------------
// 지표
//
// 엔드포인트마다 Trend 를 미리 만들어 둔다. init 단계에서 만들어야 한다 — VU 안에서
// new Trend 를 부르면 k6 가 거부한다.
// ---------------------------------------------------------------------------

const epTrend = {};
const epFail = {};
for (const endpoint of SELECTED_ENDPOINTS) {
  epTrend[endpoint.key] = new Trend(`ep_${endpoint.key}`, true);
  epFail[endpoint.key] = new Counter(`fail_${endpoint.key}`);
}
const wsConnected = new Counter('ws_connected');
const wsFailed = new Counter('ws_failed');
const wsSessionTime = new Trend('ws_session_ms', true);

// ---------------------------------------------------------------------------
// VU 구간별 지표 — 한계점(무릎)을 찾기 위한 것
//
// 요약 리포트는 실행 전체의 평균만 준다. 그래서 stress 처럼 부하를 계단식으로 올리는
// 프로파일에서 "몇 명에서 무너졌는가"를 알 수 없다. 75명 구간의 30ms 와 450명 구간의
// 8초가 뭉개져서 하나의 p95 로 나온다 — 정작 알고 싶은 답이 그 안에 묻힌다.
//
// 그래서 요청을 기록할 때 그 순간의 활성 VU 수를 함께 보고, 계단마다 별도 지표에 쌓는다.
// 리포트가 그 표를 그리면 지연이 꺾이는 지점이 눈에 보인다.
// ---------------------------------------------------------------------------

/** stress 프로파일의 계단 배율. buildScenarios 와 반드시 같아야 한다. */
const STRESS_STEPS = [0.25, 0.5, 0.75, 1.0, 1.5];

/** 이번 실행에서 거쳐 갈 VU 수들. 오름차순이어야 아래 탐색이 맞는다. */
const VU_BUCKETS = (function () {
  if (PROFILE !== 'stress') return [];
  const seen = {};
  const out = [];
  for (const factor of STRESS_STEPS) {
    const value = Math.max(1, Math.round(VUS * factor));
    if (!seen[value]) {
      seen[value] = true;
      out.push(value);
    }
  }
  return out.sort((a, b) => a - b);
})();

const bucketTrend = {};
const bucketFail = {};
const bucketReqs = {};
for (const size of VU_BUCKETS) {
  // 지표 이름에는 숫자와 밑줄만 쓸 수 있다.
  bucketTrend[size] = new Trend(`vu_${size}_ms`, true);
  bucketFail[size] = new Counter(`vu_${size}_fail`);
  bucketReqs[size] = new Counter(`vu_${size}_reqs`);
}

/**
 * 지금 활성 VU 수에 가장 가까운 계단을 고른다.
 *
 * 정확히 일치하는 값을 찾지 않는 이유: 계단을 올리고 내리는 구간에서는 VU 수가 그 사이의
 * 아무 값이나 된다(예: 150 -> 225 로 가는 중의 187). 그걸 버리면 데이터가 절반쯤 사라지고,
 * 새 구간을 만들면 계단이 수십 개로 늘어난다. 가장 가까운 계단에 붙이는 편이 읽기 쉽다.
 */
function bucketOf(active) {
  if (VU_BUCKETS.length === 0) return null;
  let best = VU_BUCKETS[0];
  let gap = Math.abs(active - best);
  for (const size of VU_BUCKETS) {
    const d = Math.abs(active - size);
    if (d < gap) {
      gap = d;
      best = size;
    }
  }
  return best;
}

// ---------------------------------------------------------------------------
// 부하 프로파일
// ---------------------------------------------------------------------------

function buildScenarios() {
  const scenarios = {};

  if (PROFILE === 'smoke') {
    // 스크립트가 맞는지만 본다. 부하가 아니다.
    scenarios.main = { executor: 'constant-vus', vus: 1, duration: '30s' };
  } else if (PROFILE === 'load') {
    // 정상 부하. 개선 전/후 숫자를 여기서 뽑는다.
    scenarios.main = {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: DURATION, target: VUS },
        { duration: RAMP, target: 0 },
      ],
      gracefulRampDown: '20s',
    };
  } else if (PROFILE === 'stress') {
    // 계단식으로 올려 무릎 지점을 찾는다. "몇 VU 까지 버티는가"가 개선을 가장 잘 보여준다.
    const steps = [0.25, 0.5, 0.75, 1.0, 1.5];
    const stages = [];
    for (const factor of steps) {
      stages.push({ duration: RAMP, target: Math.max(1, Math.round(VUS * factor)) });
      stages.push({ duration: DURATION, target: Math.max(1, Math.round(VUS * factor)) });
    }
    stages.push({ duration: RAMP, target: 0 });
    scenarios.main = { executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '20s' };
  } else if (PROFILE === 'soak') {
    // 오래 돌려 누수를 본다. 커넥션·힙이 시간에 따라 늘면 여기서 드러난다.
    scenarios.main = { executor: 'constant-vus', vus: VUS, duration: DURATION };
  } else {
    throw new Error(`알 수 없는 PROFILE: ${PROFILE} (smoke|load|stress|soak)`);
  }

  if (WS_ENABLED) {
    // WebSocket 은 REST 와 성격이 달라서(연결을 오래 유지) 별도 시나리오로 돌린다.
    scenarios.websocket = {
      executor: 'constant-vus',
      vus: WS_VUS,
      duration: WS_HOLD,
      exec: 'websocketSession',
    };
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    http_req_failed: [`rate<${MAX_FAIL_RATE}`],
    'http_req_duration{group:public}': [`p(95)<${P95_PUBLIC}`],
    'http_req_duration{group:read}': [`p(95)<${P95_READ}`],
    'http_req_duration{group:write}': [`p(95)<${P95_WRITE}`],
    'http_req_duration{group:ai}': [`p(95)<${P95_AI}`],
    checks: ['rate>0.99'],
  },
  // 실행 정보를 리포트에 남기기 위한 태그. 실행끼리 구분하는 이름이기도 하다.
  tags: { testid: TESTID },
  // 요약에 p99 와 count 를 넣는다. 기본값에는 둘 다 없어서 꼬리 지연도, 호출 횟수도 못 본다.
  // (count 가 없으면 리포트의 엔드포인트별 호출 건수가 전부 0 으로 나온다 — 실측 확인)
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count'],
  // 로컬에서 도는 dev 환경은 자체 서명 인증서를 쓰는 경우가 있다.
  insecureSkipTLSVerify: __ENV.INSECURE === '1',
};

// ---------------------------------------------------------------------------
// setup — 실행당 한 번
// ---------------------------------------------------------------------------

export function setup() {
  const startedAt = new Date().toISOString();
  const skipped = [];

  const needsLogin = SELECTED_ENDPOINTS.some((e) => e.auth !== 'none' && e.auth !== 'internal');
  const needsInternal = SELECTED_ENDPOINTS.some((e) => e.auth === 'internal');

  let pool = [];
  if (needsLogin) {
    const accounts = parseAccounts(__ENV.ACCOUNTS);
    if (accounts.length === 0) {
      throw new Error(
        '로그인이 필요한 API 를 골랐는데 ACCOUNTS 가 비어 있다.\n' +
          '  -e ACCOUNTS=\'이메일:비밀번호:역할,...\' 형태로 준다.\n' +
          '  동시에 살아 있을 수 있는 세션 수 = 계정 수다. 이 서비스는 로그인하면\n' +
          '  그 계정의 이전 세션을 끊기 때문에, 계정 1개로 VU 를 올리면 서로를 로그아웃시킨다.'
      );
    }
    pool = loginAll(BASE_URL, accounts);

    if (pool.length < 4 && VUS > 20) {
      console.warn(
        `[주의] 계정 ${pool.length}개로 VU ${VUS} 를 돌린다. 같은 토큰을 여러 VU 가 공유하므로 ` +
          `계정 단위 캐시나 락이 있으면 실제보다 좋게(또는 나쁘게) 나올 수 있다. ` +
          `가능하면 계정을 VU 의 1/5 이상 준비한다.`
      );
    }
  }

  if (needsInternal && !INTERNAL_API_KEY) {
    throw new Error(
      'AI API 를 골랐는데 INTERNAL_API_KEY 가 없다.\n' +
        '  파이썬 서버는 X-Internal-Api-Key 헤더를 요구한다(app/core/security.py).\n' +
        "  -e INTERNAL_API_KEY='...' 로 준다. 값은 파이썬의 .env 와 같아야 한다."
    );
  }

  // AI 를 고른 경우, 실제 Gemini 를 때리고 있지 않은지 확인한다. 이걸 빼먹으면 쿼터가
  // 소진되고 키가 전부 쿨다운에 들어가서 그 시점부터 전부 실패한다(비용도 든다).
  if (needsInternal) {
    const stub = checkStubMode();
    if (stub === false) {
      throw new Error(
        'AI 부하 테스트인데 파이썬이 스텁 모드가 아니다 (gemini_stub_mode=0).\n' +
          '\n' +
          '  이대로 돌리면 실제 Gemini 를 그 횟수만큼 호출한다. 무료 티어 쿼터가 소진되고\n' +
          '  키가 전부 쿨다운에 들어가면 그 시점부터 전부 실패해서, 측정하려던 성능 대신\n' +
          '  "쿼터가 언제 떨어지는가"를 재게 된다. 비용도 발생한다.\n' +
          '\n' +
          '  파이썬을 AI_STUB_MODE=true 로 다시 띄운다.\n' +
          `  확인: curl -s ${AI_BASE_URL}/metrics | grep gemini_stub_mode\n` +
          '\n' +
          '  실제 호출로 돌리는 게 의도라면 -e ALLOW_REAL_AI=1 을 준다.'
      );
    }
    if (stub === null) {
      console.warn(
        `[주의] ${AI_BASE_URL}/metrics 를 못 읽어서 스텁 모드인지 확인하지 못했다. ` +
          '실제 Gemini 를 호출하고 있을 수 있다.'
      );
    }
  }

  let ids = {};
  if (pool.length > 0) {
    ids = discoverIds(BASE_URL, pool[0], {
      chatRoomId: numberOrNull(__ENV.CHAT_ROOM_ID),
      negotiationId: numberOrNull(__ENV.NEGOTIATION_ID),
      contractId: numberOrNull(__ENV.CONTRACT_ID),
    });
  }

  // 못 도는 엔드포인트를 미리 걸러낸다. 조용히 빼지 않고 리포트에 남긴다.
  const runnable = [];
  for (const endpoint of SELECTED_ENDPOINTS) {
    const missing = (endpoint.needs || []).filter((need) => ids[need] === undefined);
    if (missing.length) {
      skipped.push(`${endpoint.key} — ${missing.join(', ')} 를 찾지 못했다 (해당 데이터가 없다)`);
      continue;
    }
    if (
      (endpoint.auth === 'CLIENT' || endpoint.auth === 'FREELANCER') &&
      !pool.some((a) => a.role === endpoint.auth)
    ) {
      skipped.push(`${endpoint.key} — ${endpoint.auth} 역할 계정이 없다`);
      continue;
    }
    runnable.push(endpoint.key);
  }

  if (runnable.length === 0) {
    throw new Error(
      '실행할 수 있는 API 가 하나도 없다.\n  ' + (skipped.join('\n  ') || '고른 API 가 없다.')
    );
  }

  console.log(`\n[실행] ${runnable.length}개 API / 프로파일 ${PROFILE} / testid=${TESTID}`);
  for (const line of skipped) console.warn(`[제외] ${line}`);

  return { pool, ids, runnable, skipped, startedAt };
}

function numberOrNull(raw) {
  if (raw === undefined || raw === null || raw === '') return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

/** 파이썬이 스텁 모드인지 본다. true/false/null(확인 실패). */
function checkStubMode() {
  if (__ENV.ALLOW_REAL_AI === '1') return true;
  const res = http.get(`${AI_BASE_URL}/metrics`, {
    tags: { name: 'setup:stubcheck' },
    timeout: '5s',
  });
  if (res.status !== 200) return null;
  const body = String(res.body);
  if (body.indexOf('gemini_stub_mode 1') !== -1) return true;
  if (body.indexOf('gemini_stub_mode 0') !== -1) return false;
  return null;
}

// ---------------------------------------------------------------------------
// REST 시나리오
// ---------------------------------------------------------------------------

export default function (data) {
  const vuId = exec.vu.idInTest;
  const seq = exec.scenario.iterationInTest;

  for (const key of data.runnable) {
    const endpoint = findEndpoint(key);
    callEndpoint(endpoint, data, vuId, seq);
  }

  // 사람이 화면을 보는 시간. 0 이면 서버가 아니라 클라이언트 한계를 재게 된다.
  if (THINK_MS > 0) sleep(THINK_MS / 1000);
}

function callEndpoint(endpoint, data, vuId, seq) {
  const isPython = endpoint.target === 'python';
  const base = isPython ? AI_BASE_URL : BASE_URL;

  let account = null;
  let headers;

  if (endpoint.auth === 'internal') {
    headers = { 'Content-Type': 'application/json', 'X-Internal-Api-Key': INTERNAL_API_KEY };
  } else if (endpoint.auth === 'none') {
    headers = { 'Content-Type': 'application/json' };
  } else {
    account = pickAccount(data.pool, vuId, endpoint.auth);
    if (!account) return; // setup 에서 걸러졌어야 한다. 방어적으로 둔다.
    headers = bearerHeaders(account);
  }

  const path = resolvePath(endpoint, data.ids);
  if (path === null) return;

  const body = endpoint.body ? JSON.stringify(endpoint.body({ testid: TESTID, seq, vuId })) : null;

  const res = http.request(endpoint.method, `${base}${path}`, body, {
    headers,
    // name 은 경로 템플릿이다. 실제 URL 을 쓰면 ID 마다 시리즈가 생겨 리포트가 터진다.
    tags: { name: `${endpoint.method} ${endpoint.path}`, group: endpoint.group, api: endpoint.key },
  });

  epTrend[endpoint.key].add(res.timings.duration);

  const ok = check(res, {
    [`${endpoint.key} 2xx`]: (r) => r.status >= 200 && r.status < 300,
  });

  // 무릎을 찾기 위한 VU 구간별 기록. stress 가 아니면 버킷이 없어 건너뛴다.
  const bucket = bucketOf(exec.instance.vusActive);
  if (bucket !== null) {
    bucketTrend[bucket].add(res.timings.duration);
    bucketReqs[bucket].add(1);
    if (!ok) bucketFail[bucket].add(1);
  }

  if (!ok) {
    epFail[endpoint.key].add(1);
    // 처음 몇 번만 찍는다. 전부 찍으면 콘솔이 로그로 덮여서 진행 상황이 안 보인다.
    if (seq < 3) {
      console.error(
        `[실패] ${endpoint.key} ${endpoint.method} ${path} -> ${res.status} ` +
          `${String(res.body).slice(0, 200)}`
      );
    }
  }
}

// ---------------------------------------------------------------------------
// WebSocket 시나리오
//
// 연결을 맺고 구독한 뒤 유지한다. 실제 사용자는 화면을 열어 두고 있을 뿐 대부분 아무것도
// 보내지 않는다 — 그 상태를 재현해야 "동시 접속이 늘면 어떻게 되는가"를 볼 수 있다.
//
// ★ 태스크가 2대 이상이면 이 테스트에서 이벤트 유실이 재현된다. 브로커가 인메모리
//   SimpleBroker 라서 인스턴스 간 push 가 전달되지 않는다(StompWebSocketConfig 주석).
//   부하 테스트로 드러낼 수 있는 실제 구조 문제다.
// ---------------------------------------------------------------------------

export function websocketSession(data) {
  if (!data.pool.length) return;

  const account = pickAccount(data.pool, exec.vu.idInTest, null);
  const url = websocketUrl(BASE_URL);
  const started = Date.now();

  const res = ws.connect(url, { headers: cookieHeaders(account) }, function (socket) {
    let connected = false;

    socket.on('open', function () {
      socket.send(connectFrame(hostOf(BASE_URL)));
    });

    socket.on('message', function (raw) {
      const command = commandOf(raw);

      if (command === 'CONNECTED' && !connected) {
        connected = true;
        wsConnected.add(1);

        const destinations = destinationsFor(account, data.ids);
        destinations.forEach(function (destination, index) {
          socket.send(subscribeFrame(`sub-${index}`, destination));
        });

        // 하트비트. 서버가 10초 주기를 기대하므로 그보다 짧게 보낸다. 안 보내면 부하와
        // 무관하게 연결이 끊겨서 "부하를 받으면 WebSocket 이 죽는다"는 오진이 나온다.
        socket.setInterval(function () {
          socket.send('\n');
        }, HEARTBEAT_MS / 2);
      }

      if (command === 'ERROR') {
        wsFailed.add(1);
        console.error(`[WS] 서버가 ERROR 프레임을 보냈다: ${String(raw).slice(0, 200)}`);
        socket.close();
      }
    });

    socket.on('error', function (e) {
      wsFailed.add(1);
      console.error(`[WS] 소켓 오류: ${e && e.error ? e.error() : e}`);
    });

    // 유지 시간이 끝나면 정리하고 나간다.
    socket.setTimeout(function () {
      if (connected) socket.send(disconnectFrame());
      socket.close();
    }, parseDurationMs(WS_HOLD));
  });

  wsSessionTime.add(Date.now() - started);
  check(res, { 'WS 핸드셰이크 101': (r) => r && r.status === 101 });
}

function hostOf(url) {
  return String(url).replace(/^https?:\/\//, '').split('/')[0];
}

/** '3m' / '90s' / '1h' 를 ms 로. k6 는 이 변환을 노출하지 않는다. */
function parseDurationMs(text) {
  const match = String(text).match(/^(\d+)([smh])$/);
  if (!match) return 60000;
  const value = Number(match[1]);
  return match[2] === 'h' ? value * 3600000 : match[2] === 'm' ? value * 60000 : value * 1000;
}

// ---------------------------------------------------------------------------
// 결과 저장
// ---------------------------------------------------------------------------

export function handleSummary(data) {
  const meta = {
    testid: TESTID,
    profile: PROFILE,
    baseUrl: BASE_URL,
    startedAt: new Date().toISOString(),
    accounts: (data.setup_data && data.setup_data.pool ? data.setup_data.pool : []).length,
    apiCount: SELECTED_ENDPOINTS.length,
    skipped: (data.setup_data && data.setup_data.skipped) || [],
  };

  const out = {};
  out['stdout'] = textSummary(data, meta);
  out[`k6/reports/${TESTID}.html`] = htmlReport(data, meta);
  out[`k6/reports/${TESTID}.json`] = JSON.stringify({ meta, metrics: data.metrics }, null, 2);
  return out;
}
