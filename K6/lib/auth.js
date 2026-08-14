/**
 * 로그인과 토큰 풀.
 *
 * ---------------------------------------------------------------------------
 * 이 파일이 존재하는 이유 — 이 서비스의 로그인에는 부하 테스트를 깨뜨리는 성질이 두 개 있다.
 *
 * 1) 로그인하면 그 계정의 **이전 세션이 즉시 끊긴다**
 *    (AuthController: "성공 시 ... 이전 기기의 세션은 즉시 끊깁니다")
 *
 *    VU 100개가 같은 계정으로 각자 로그인하면, 나중 로그인이 앞선 VU 의 토큰을 죽인다.
 *    결과는 401 폭풍인데, 그래프만 보면 "부하를 받아 인증이 실패한다"처럼 보인다.
 *    실제로는 스크립트가 스스로를 로그아웃시킨 것이다.
 *
 *    -> 그래서 로그인은 setup() 에서 **계정당 정확히 한 번**만 한다. VU 루프 안에서는
 *       절대 로그인하지 않고, 발급받은 토큰을 나눠 쓴다.
 *       동시에 살아 있을 수 있는 세션 수 = 준비된 계정 수.
 *
 * 2) 로그인 실패가 쌓이면 **IP 가 2시간 차단된다**
 *    (AuthSettings: ipFailMax=20 / ipFailWindow=1h / ipBlockDuration=2h,
 *     계정별로는 loginFailMax=5 회면 잠금)
 *
 *    k6 는 단일 IP 다. 비밀번호를 틀린 채로 20 VU 를 돌리면 첫 몇 초 만에 한도를 넘기고
 *    그 IP 는 2시간 동안 아무것도 못 한다 — 그날 테스트가 끝난다.
 *
 *    -> 그래서 실패 3번이면 즉시 중단한다. 남은 실패 예산(20회)을 태우지 않기 위해서다.
 *       재시도도 하지 않는다. 로그인 실패는 부하 문제가 아니라 설정 문제다.
 *
 * ---------------------------------------------------------------------------
 * 토큰은 응답 본문이 아니라 **쿠키**로 온다
 *   LoginResponse 주석: "토큰은 HttpOnly 쿠키로 나가므로 본문에 담지 않는다"
 *   REST 는 Authorization: Bearer 도 받지만(GlobalJwtAuthenticationFilter),
 *   WebSocket 핸드셰이크는 **쿠키만** 읽는다(JwtHandshakeInterceptor).
 *   그래서 쿠키 값을 그대로 들고 있다가 용도에 따라 헤더를 바꿔 쓴다.
 */

import http from 'k6/http';

/** 로그인 실패를 몇 번까지 허용할지. IP 차단 한도(20)보다 한참 낮게 둔다. */
const MAX_LOGIN_FAILURES = 3;

/**
 * "email:password:ROLE,email2:password2:ROLE" 형태를 파싱한다.
 *
 * 역할이 필요한 이유: LoginRequest 의 role 이 @NotNull 이고, 주석에 "같은 이메일이라도
 * 역할이 다르면 다른 계정이다"라고 되어 있다. 즉 역할은 조회 결과가 아니라 입력이다.
 */
export function parseAccounts(raw) {
  const accounts = [];
  for (const chunk of String(raw || '').split(',')) {
    const line = chunk.trim();
    if (!line) continue;

    const parts = line.split(':');
    if (parts.length !== 3) {
      throw new Error(
        `계정 형식이 틀렸다: "${line}"\n` +
          `형식은 이메일:비밀번호:역할 이다. 예) tester1@pairing.com:Passw0rd!:FREELANCER\n` +
          `비밀번호에 ':' 가 들어가면 이 형식으로는 표현할 수 없다. 테스트 계정의 비밀번호를 바꾼다.`
      );
    }

    const role = parts[2].trim().toUpperCase();
    if (role !== 'CLIENT' && role !== 'FREELANCER') {
      throw new Error(`역할은 CLIENT 또는 FREELANCER 여야 한다: "${parts[2]}"`);
    }
    accounts.push({ email: parts[0].trim(), password: parts[1], role });
  }
  return accounts;
}

/**
 * 계정 목록을 전부 로그인시켜 토큰 풀을 만든다. **setup() 에서만 부른다.**
 *
 * @returns [{ email, role, accountId, token }]
 */
export function loginAll(baseUrl, accounts) {
  const pool = [];
  let failures = 0;

  for (const account of accounts) {
    const res = http.post(
      `${baseUrl}/api/v1/auth/login`,
      JSON.stringify({ email: account.email, password: account.password, role: account.role }),
      {
        headers: { 'Content-Type': 'application/json' },
        // 로그인은 부하 측정 대상이 아니다. 태그를 따로 붙여 본 테스트 통계와 섞이지 않게 한다.
        tags: { name: 'setup:login' },
      }
    );

    if (res.status !== 200) {
      failures += 1;
      console.error(
        `[로그인 실패] ${account.email} (${account.role}) -> HTTP ${res.status}\n` +
          `  응답: ${String(res.body).slice(0, 300)}`
      );

      if (failures >= MAX_LOGIN_FAILURES) {
        throw new Error(
          `로그인이 ${failures}번 실패해서 중단한다.\n` +
            `\n` +
            `더 시도하지 않는 이유: 이 서버는 IP 기준으로 로그인 실패를 세고(ipFailMax=20/1시간),\n` +
            `넘기면 그 IP 를 2시간 차단한다(ipBlockDuration=2h). 계정별로도 5회면 잠긴다.\n` +
            `계속 두드리면 오늘 테스트를 아예 못 하게 된다.\n` +
            `\n` +
            `확인할 것:\n` +
            `  1. 계정과 비밀번호가 맞는지 (틀린 비밀번호로 돌리면 실패 예산이 그냥 소모된다)\n` +
            `  2. 역할이 맞는지 — 같은 이메일이라도 역할이 다르면 다른 계정이다\n` +
            `  3. BASE_URL 이 맞는지: ${baseUrl}\n` +
            `  4. 이미 차단된 상태는 아닌지 (그렇다면 최대 2시간 기다려야 한다)`
        );
      }
      continue;
    }

    const token = extractAccessToken(res);
    if (!token) {
      throw new Error(
        `로그인은 200 인데 accessToken 쿠키가 없다: ${account.email}\n` +
          `  받은 쿠키: ${Object.keys(res.cookies || {}).join(', ') || '(없음)'}\n` +
          `  이 서비스는 토큰을 본문이 아니라 HttpOnly 쿠키로 내려준다(LoginResponse 주석 참고).\n` +
          `  프록시나 게이트웨이가 Set-Cookie 를 지우고 있지 않은지 확인한다.`
      );
    }

    let accountId = null;
    try {
      accountId = res.json('data.accountId');
    } catch (e) {
      // 본문 형태가 달라져도 토큰만 있으면 테스트는 돈다. 식별자는 로그용이다.
    }

    pool.push({ email: account.email, role: account.role, accountId, token });
    console.log(`[로그인] ${account.email} (${account.role}) accountId=${accountId}`);
  }

  if (pool.length === 0) {
    throw new Error('로그인에 성공한 계정이 하나도 없다. 위 오류를 먼저 해결한다.');
  }
  return pool;
}

/** Set-Cookie 에서 accessToken 값을 꺼낸다. */
function extractAccessToken(res) {
  const cookies = res.cookies || {};
  const entries = cookies['accessToken'];
  if (!entries || entries.length === 0) return null;
  return entries[0].value;
}

/**
 * VU 가 쓸 계정을 고른다.
 *
 * VU 번호로 나눠 주므로 같은 VU 는 항상 같은 계정을 쓴다. 매번 무작위로 고르면 같은 계정이
 * 여러 VU 에 동시에 걸려서, 캐시나 세션 관련 지표를 볼 때 원인을 가려내기 어려워진다.
 *
 * 역할이 필요한 API 면 그 역할을 가진 계정 중에서 고른다. 없으면 null 을 주고,
 * 호출자는 그 API 를 건너뛴다.
 */
export function pickAccount(pool, vuId, requiredRole) {
  const candidates =
    requiredRole === 'CLIENT' || requiredRole === 'FREELANCER'
      ? pool.filter((a) => a.role === requiredRole)
      : pool;

  if (candidates.length === 0) return null;
  return candidates[(vuId - 1) % candidates.length];
}

/** REST 호출용 헤더. */
export function bearerHeaders(account) {
  const headers = { 'Content-Type': 'application/json' };
  if (account) headers['Authorization'] = `Bearer ${account.token}`;
  return headers;
}

/**
 * WebSocket 핸드셰이크용 헤더.
 *
 * JwtHandshakeInterceptor 는 쿼리 파라미터가 아니라 accessToken **쿠키**만 읽는다.
 * Bearer 헤더를 보내도 핸드셰이크는 거절된다.
 */
export function cookieHeaders(account) {
  return { Cookie: `accessToken=${account.token}` };
}
