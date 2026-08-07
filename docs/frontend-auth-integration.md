# 프론트엔드 연동 가이드 — 인증 · 회원가입 · 계정 찾기

이 문서만 읽고 인증 화면 전체를 구현할 수 있도록 쓴 문서입니다. AI 에이전트가 읽고 코드를 생성하는 것을 전제로,
추측이 필요한 부분을 남기지 않았습니다.

**범위**: 이메일 인증 / 회원가입(클라이언트·프리랜서·소셜) / 로그인·세션 / 아이디 찾기 / 비밀번호 찾기 / 계정 잠금 해제 / 비밀번호 변경
**범위 밖**: 프로젝트·매칭·협상 등 나머지 도메인 → [.ai/API.md](../.ai/API.md)

이 범위는 **서버 구현이 끝나 있습니다.** 응답이 고정값이 아니라 실제로 동작합니다.

---

## 0. 시작하기 전에 반드시 맞출 것

세 값이 어긋나면 로그인은 되는데 이후 요청이 전부 401 이 됩니다. 가장 흔한 사고입니다.

| 값 | 서버 설정 | 프론트에서 해야 할 일 |
| --- | --- | --- |
| 프론트 주소 | `APP_FRONT_BASE_URL` (기본 `http://localhost:17000`) | 비밀번호 재설정 메일 링크가 이 주소로 만들어진다. 실제 프론트 주소와 같아야 한다 |
| 허용 오리진 | `CORS_ALLOWED_ORIGINS` (기본 `http://localhost:17000,http://127.0.0.1:17000`) | 프론트를 띄운 오리진이 목록에 있어야 한다. `localhost` 와 `127.0.0.1` 은 다른 오리진이다 |
| API 주소 | `http://localhost:8080` | 모든 경로 앞에 `/api/v1` 이 붙는다 |

`*` 와일드카드는 쓸 수 없습니다. 쿠키를 주고받으려면 오리진을 명시해야 합니다.

---

## 1. 공통 규약

### 1-1. 응답 봉투

성공이든 실패든 항상 아래 두 형태 중 하나입니다. `data` 만 꺼내 쓰면 됩니다.

```jsonc
// 성공
{
  "timestamp": "2026-08-07T05:12:33.412Z",
  "status": 200,
  "code": "LOGIN_SUCCESS",       // 성공 코드. 화면 분기에는 쓰지 않는다
  "message": "로그인에 성공했습니다.",
  "data": { }                    // 없으면 null
}

// 실패
{
  "timestamp": "2026-08-07T05:12:33.412Z",
  "status": 401,
  "errorCode": "AU_001",         // 화면 분기는 이 값으로 한다
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "traceId": "72a6691a"          // 서버 로그 추적용. 문의 시 첨부
}
```

- **분기는 `errorCode` 로** 합니다. `message` 는 문구가 바뀔 수 있고, HTTP status 는 여러 원인이 겹칩니다.
- `message` 는 사용자에게 그대로 보여줘도 되게 쓰여 있습니다. 별도 번역 테이블을 만들지 마세요.
- 본문의 `status` 는 실제 HTTP 상태와 같습니다. 회원가입 3종만 **201**, 나머지 성공은 **200** 입니다.

### 1-2. 쿠키와 `credentials`

토큰은 응답 본문에 없습니다. **HttpOnly 쿠키로만** 나갑니다. JS 에서 읽을 수 없고, 읽을 필요도 없습니다.

| 쿠키 | 수명 | 속성 |
| --- | --- | --- |
| `accessToken` | 30분 | HttpOnly, Path=/, SameSite=Lax(로컬) / None+Secure(운영) |
| `refreshToken` | 7일 | 동일 |

**모든 요청에 `credentials: 'include'` 를 넣어야 합니다.** 하나라도 빠지면 그 요청만 401 이 납니다.

```ts
// api.ts — 프로젝트 전역에서 이 인스턴스만 쓴다
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  withCredentials: true,            // 필수
  headers: { 'Content-Type': 'application/json' },
});

// fetch 를 쓴다면
fetch(`${BASE}/api/v1/auth/me`, { credentials: 'include' });
```

### 1-3. 401 이 왔을 때

401 은 원인이 4가지고 대응이 전부 다릅니다. `errorCode` 로 갈라야 합니다.

| errorCode | 뜻 | 프론트 대응 |
| --- | --- | --- |
| `GLOBAL_006` | 토큰 없음 (비로그인) | 로그인 페이지로 |
| `GLOBAL_009` | Access 만료 | **`POST /auth/refresh` 후 원래 요청 1회 재시도** |
| `GLOBAL_010` | 토큰 위조·손상 | 쿠키 정리 후 로그인 페이지로 |
| `GLOBAL_011` | **다른 기기에서 로그인해 세션이 끊김** | "다른 기기에서 로그인되었습니다" 모달 → 로그인 페이지로 |

```ts
// 인터셉터 한 곳에서만 처리한다. 화면마다 만들면 refresh 가 중복 호출된다.
let refreshing: Promise<void> | null = null;

api.interceptors.response.use(undefined, async (error) => {
  const { response, config } = error;
  if (response?.status !== 401 || config._retried) throw error;

  const code = response.data?.errorCode;
  if (code !== 'GLOBAL_009') {
    if (code === 'GLOBAL_011') showDuplicateLoginModal();
    redirectToLogin();
    throw error;
  }

  refreshing ??= api.post('/api/v1/auth/refresh').finally(() => { refreshing = null; });
  await refreshing;

  config._retried = true;          // 무한 루프 방지
  return api(config);
});
```

`refresh` 자체가 실패하면(`AU_015` / `AU_016`) 재시도하지 말고 로그인 페이지로 보냅니다.

### 1-4. 역할(role)

`CLIENT` / `FREELANCER` / `ADMIN` 세 가지입니다.

**같은 사람이 클라이언트 계정과 프리랜서 계정을 따로 가질 수 있습니다.** 이메일·휴대폰 유니크는 역할별로 걸려 있습니다.
그래서 로그인·중복확인·비밀번호 찾기에 `role` 이 반드시 들어갑니다. 로그인 화면의 탭(클라이언트/프리랜서)이 이 값입니다.

---

## 2. 화면 → API 대응표

| 화면 | 호출 순서 |
| --- | --- |
| 회원가입 (공통) | `GET /terms?role=` → `GET /auth/exists/*` → `POST /auth/email-verifications` → `POST /auth/email-verifications/confirm` → `POST /auth/signup/*` |
| 클라이언트 로그인 / 프리랜서 로그인 | `POST /auth/login` |
| 소셜 로그인 (프리랜서만) | `GET /auth/social/{provider}/authorize` → 리다이렉트 → `POST /auth/social/{provider}/callback` → (신규면) `POST /auth/signup/freelancer/social` |
| 아이디 찾기 | `POST /auth/find-email` |
| 비밀번호 찾기 | `POST /auth/password/reset-requests` → **메일 링크** → `POST /auth/password/reset-confirm` |
| 로그인 제한(잠금) 해제 | `POST /auth/email-verifications` (purpose=UNLOCK) → `POST /auth/unlock` |
| 비밀번호 변경 (마이페이지) | `POST /auth/email-verifications` (purpose=PASSWORD_CHANGE) → `POST /auth/email-verifications/confirm` → `PATCH /auth/password` |
| 세션 유지 | `GET /auth/me`, `POST /auth/refresh`, `POST /auth/logout` |

---

## 3. 이메일 인증 (인증코드 방식)

회원가입·잠금해제 앞단에 공통으로 쓰입니다. **비밀번호 찾기는 코드가 아니라 링크 방식이라 여기 해당하지 않습니다.**

### 3-1. 코드 발송

```http
POST /api/v1/auth/email-verifications
Content-Type: application/json

{ "email": "user@pairing.com", "purpose": "SIGNUP" }
```

`purpose`: `SIGNUP`(회원가입) / `UNLOCK`(계정 잠금 해제) / `PASSWORD_CHANGE`(마이페이지 비밀번호 변경) / `PROFILE_UPDATE`(프로필 변경 확인)

용도가 다르면 코드도 다릅니다. `SIGNUP` 으로 받은 코드로 비밀번호를 바꿀 수 없습니다.

```jsonc
// 200
"data": {
  "expiresAt": "2026-08-07T05:15:33",  // 이 시각까지 유효 (3분)
  "remainingSendCount": 14             // 1시간 내 남은 발송 횟수 (총 15회)
}
```

- `expiresAt` 으로 **카운트다운 타이머**를 그립니다. 클라이언트 시계 대신 이 값을 기준으로 하세요.
- `remainingSendCount` 가 0 이 되면 재발송 버튼을 비활성화합니다. 그래도 호출하면 `AU_003` (429).

### 3-2. 코드 확인

```http
POST /api/v1/auth/email-verifications/confirm

{ "email": "user@pairing.com", "purpose": "SIGNUP", "code": "482913" }
```

성공하면 `data: null` 입니다. 서버가 "이 이메일은 이 용도로 인증됨" 표시를 **30분간** 보관합니다.
이 표시를 보고 뒤이은 요청(가입 제출 / 비밀번호 변경)이 통과합니다.

> **30분 안에 가입 제출을 끝내야 합니다.** 넘기면 가입 요청이 `AU_006`(이메일 인증 미완료)로 거절됩니다.
> 가입 폼이 길어 30분을 넘길 수 있으면, 제출 직전에 인증 단계로 되돌리는 안내를 준비해 두세요.

| errorCode | 상황 | 화면 처리 |
| --- | --- | --- |
| `AU_004` | 코드 불일치 | 입력칸 아래 에러. 남은 시도 횟수는 서버가 세고, 5회 초과 시 `AU_012` |
| `AU_005` | 코드 만료 (3분) | "인증코드가 만료되었습니다. 재발송해 주세요." + 재발송 버튼 활성화 |
| `AU_012` | 시도 5회 초과 → 코드 폐기 | 재발송부터 다시 |
| `AU_003` | 1시간 15회 초과 | 429. 재발송 버튼 잠금 |
| `AU_026` | 메일 발송 실패 (SMTP 장애) | 500. "잠시 후 다시 시도해 주세요" |

**UNLOCK 만 confirm 을 호출하지 않습니다.** 코드를 `POST /auth/unlock` 에 그대로 넣으면 서버가 안에서 검증합니다. (9절)
`SIGNUP` 과 `PASSWORD_CHANGE` 는 confirm 을 반드시 거쳐야 합니다.

---

## 4. 회원가입

### 4-1. 약관 조회

```http
GET /api/v1/terms?role=CLIENT
```

가입 화면 동의 항목은 **항상 셋**입니다. 순서대로 그리면 됩니다.

| 순서 | code | 화면 문구 | 필수 |
| --- | --- | --- | --- |
| 1 | `SERVICE` | 서비스 이용약관 동의 | **필수** |
| 2 | `PRIVACY_CONSENT` | 개인정보 수집 및 이용 동의 | **필수** |
| 3 | `MARKETING` | 마케팅 정보 수신 동의 | 선택 |

```jsonc
"data": [
  { "termsId": 1, "code": "SERVICE",         "type": "AGREEMENT",
    "title": "서비스 이용약관 동의",          "version": "v1.0",
    "required": true,  "effectiveAt": "2026-08-07T00:00:00", "content": "제1조(목적) ..." },
  { "termsId": 3, "code": "PRIVACY_CONSENT", "type": "AGREEMENT",
    "title": "개인정보 수집 및 이용 동의",     "version": "v1.0",
    "required": true,  "effectiveAt": "2026-08-07T00:00:00", "content": "..." },
  { "termsId": 4, "code": "MARKETING",       "type": "AGREEMENT",
    "title": "마케팅 정보 수신 동의",          "version": "v1.0",
    "required": false, "effectiveAt": "2026-08-07T00:00:00", "content": "..." }
]
```

- 가입 요청의 `agreements[]` 에는 **응답으로 받은 `termsId` 를 그대로** 넣습니다.
  하드코딩하면 약관 버전이 올라갈 때 `TM_003`(알 수 없는 약관)이 납니다.
- `required: true` 인 항목을 하나라도 `agreed: false` 로 보내면 `TM_002`.
- **선택 항목도 `agreements[]` 에 넣어야 합니다.** 마케팅에 동의하지 않았으면 빼지 말고
  `{ "termsId": 4, "agreed": false }` 로 보냅니다. 거부 이력도 남겨야 하기 때문입니다.
- 서비스 이용약관은 역할에 따라 내용이 다르지만 `code` 는 둘 다 `SERVICE` 입니다.
  `role` 파라미터로 맞는 쪽 한 건만 내려가므로 화면에서 분기할 필요가 없습니다.
- "전체 동의" 체크박스는 화면 기능입니다. 서버로는 항목별 값을 따로 보냅니다.

#### 개인정보 처리방침은 동의 항목이 아닙니다

`GET /terms` 에는 **개인정보 처리방침이 나오지 않습니다.** 「개인정보 보호법」 제30조상
수립·공개 의무이지 동의를 받는 문서가 아니기 때문입니다. 가입 화면에 체크박스로 넣지 마세요.

푸터의 문서 링크(이용약관 / 개인정보 처리방침)는 아래를 씁니다.

```http
GET /api/v1/terms/documents?role=CLIENT
```

동의 항목 전문 3건에 `PRIVACY_POLICY`(type = `POLICY`) 1건이 더해져 4건이 옵니다.
`type` 이 `AGREEMENT` 인 것만 동의 대상이라고 판단하세요.

### 4-2. 중복 확인

입력칸 블러 시점에 호출합니다. 셋 다 `data: { "duplicated": true|false }` 입니다.

```http
GET /api/v1/auth/exists/email?email=user@pairing.com&role=CLIENT
GET /api/v1/auth/exists/phone?phone=01012345678&role=CLIENT
GET /api/v1/auth/exists/business-no?businessNo=1234567890
```

`role` 이 필요한 이유는 1-4 를 보세요. 사업자번호만 역할과 무관합니다.

### 4-3. 클라이언트 가입

```http
POST /api/v1/auth/signup/client        → 201
```

```jsonc
{
  "companyName": "주식회사 페어링",
  "businessNo": "1234567890",          // 하이픈 없이 숫자 10자리
  "businessField": "IT_CONTENTS_AI",   // GET /meta/business-fields
  "employeeCount": "SIZE_10_49",       // GET /meta/employee-counts
  "email": "owner@pairing.com",
  "name": "김담당",
  "phone": "01012345678",
  "password": "Pairing!2026",
  "passwordConfirm": "Pairing!2026",
  "card":        { "cardNumber": "1234-5678-9123-4567", "cardBrand": "신한카드" },
  "bankAccount": { "bankCode": "088", "accountNo": "110-123-456789", "accountHolder": "김담당" },
  "agreements": [ { "termsId": 1, "agreed": true }, { "termsId": 2, "agreed": true } ]
}
```

```jsonc
// 201
"data": { "accountId": 7, "role": "CLIENT" }
```

- **가입 성공만으로 로그인되지 않습니다.** 쿠키가 발급되지 않으니 로그인 화면으로 보내세요. (소셜 가입만 예외)
- 카드·계좌 번호는 하이픈을 넣어도 됩니다. 서버가 숫자만 남겨 암호화 저장합니다.
- `bankCode` 는 `GET /meta/banks` 의 코드입니다. 목록에 없으면 `AC_006`.

### 4-4. 프리랜서 가입

```http
POST /api/v1/auth/signup/freelancer    → 201
```

```jsonc
{
  "name": "홍길동",
  "phone": "01012345678",
  "email": "user@pairing.com",
  "password": "Pairing!2026",
  "passwordConfirm": "Pairing!2026",
  "birthDate": "1995-03-01",           // 만 18세 이상만 가입 가능
  "card": { }, "bankAccount": { }, "agreements": [ ]
}
```

만 18세 미만이면 `AU_025`. 생년월일 입력 시점에 프론트에서도 막아 주세요.

### 4-5. 소셜 가입 (프리랜서 전용)

5-3 을 보세요. 소셜 로그인 흐름 안에 있습니다.

### 4-6. 가입 실패 코드

| errorCode | HTTP | 상황 |
| --- | --- | --- |
| `AU_006` | 400 | 이메일 인증을 안 했거나 30분이 지남 |
| `AU_007` / `AU_008` / `AU_009` | 409 | 이메일 / 휴대폰 / 사업자번호 중복 |
| `AU_010` | 400 | 비밀번호 형식 위반 (대소문자+숫자+특수문자, 8~20자) |
| `AU_011` | 400 | 비밀번호 확인 불일치 |
| `AU_013` / `TM_002` | 400 | 필수 약관 미동의 |
| `TM_003` | 400 | 존재하지 않는 `termsId` 를 보냄 |
| `AU_021` | 403 | 탈퇴 후 30일이 지나지 않아 재가입 제한 |
| `AU_025` | 400 | 만 18세 미만 |
| `GLOBAL_002` | 400 | 형식 검증 실패 (필수값 누락 등) |

---

## 5. 로그인 · 세션

### 5-1. 로그인

```http
POST /api/v1/auth/login

{ "email": "user@pairing.com", "password": "Pairing!2026", "role": "FREELANCER" }
```

```jsonc
// 200 — 응답 헤더로 accessToken / refreshToken 쿠키가 함께 내려온다
"data": { "accountId": 7, "role": "FREELANCER", "name": "홍길동", "tempPassword": false }
```

> **`tempPassword: true` 면 홈으로 보내지 말고 비밀번호 변경 화면으로 보내야 합니다.**
>
> 주의: **이 강제는 현재 프론트엔드 책임입니다.** 서버에 `AU_017`(임시 비밀번호 상태) 코드가 정의돼 있지만
> 아직 아무 데서도 던지지 않습니다. 임시 비밀번호 상태로도 다른 API 가 그냥 호출됩니다.
> 라우터 가드에서 `tempPassword === true` 면 비밀번호 변경 화면과 로그아웃 외 모든 경로를 막아 주세요.

**중복 로그인은 불가능합니다.** 새로 로그인하면 이전 기기의 세션이 끊기고, 이전 기기는 다음 요청에서 `GLOBAL_011` 을 받습니다.

| errorCode | HTTP | 화면 처리 |
| --- | --- | --- |
| `AU_001` | 401 | "이메일 또는 비밀번호가 올바르지 않습니다." **어느 쪽이 틀렸는지 구분해 주지 않습니다** |
| `AU_002` | 423 | 5회 연속 실패로 계정 잠금 → 잠금 해제 화면으로 (7절) |
| `AU_014` | 429 | IP 차단. 1시간에 20회 실패하면 2시간 차단 |
| `AU_022` | 403 | 관리자가 정지시킨 계정 |

실패 횟수는 서버가 셉니다. 프론트에서 카운트하지 마세요. 새로고침하면 어긋납니다.

### 5-2. 세션 유지 3종

```http
GET  /api/v1/auth/me         현재 사용자 → { accountId, email, role, name, tempPassword }
POST /api/v1/auth/refresh    Access 재발급 + Refresh 회전 → LoginResponse 와 동일
POST /api/v1/auth/logout     Redis 토큰·세션 삭제 + 쿠키 만료
```

- 앱 진입 시 `GET /auth/me` 한 번으로 로그인 상태를 복원합니다. 401 이면 비로그인입니다.
- `refresh` 는 호출할 때마다 Refresh 토큰이 **회전**합니다. 이전 값은 즉시 무효입니다. 동시에 여러 번 호출하지 마세요(1-3 인터셉터 참고).
- 로그아웃은 실패해도 프론트 상태는 초기화하세요.

### 5-3. 소셜 로그인 (프리랜서 전용)

```
[1] GET /api/v1/auth/social/{provider}/authorize?returnUrl=/mypage
      provider = kakao | google
    → data: { "authorizeUrl": "https://kauth.kakao.com/...", "state": "..." }
    → window.location.href = authorizeUrl

[2] 공급자가 프론트 리다이렉트 URI 로 ?code=...&state=... 를 붙여 되돌려 보냄

[3] POST /api/v1/auth/social/{provider}/callback   { "code": "...", "state": "..." }
```

콜백 응답은 `status` 로 갈립니다.

```jsonc
// 기존 회원 — 이 시점에 쿠키가 발급됨. 바로 로그인 완료
{ "status": "LOGIN", "login": { "accountId": 7, "role": "FREELANCER", "name": "홍길동", "tempPassword": false } }

// 신규 — 추가 정보 입력 화면으로
{ "status": "SIGNUP_REQUIRED", "signUpTicket": "9f2c...", "email": "user@gmail.com", "name": "홍길동" }
```

`SIGNUP_REQUIRED` 면 티켓을 들고 가입을 마칩니다. **티켓 유효시간 30분**, 넘기면 `AU_020`.

```http
POST /api/v1/auth/signup/freelancer/social     → 201, 성공 시 쿠키 발급(= 즉시 로그인)

{ "signUpTicket": "9f2c...", "name": "홍길동", "phone": "01012345678",
  "birthDate": "1995-03-01", "card": {}, "bankAccount": {}, "agreements": [] }
```

- **이메일을 보내지 않습니다.** 티켓에 담긴 공급자 이메일을 서버가 씁니다. 화면에는 읽기 전용으로 표시하세요.
- 이 경로만 가입 성공 시 바로 로그인 상태가 됩니다.
- `state` 는 5분 유효한 CSRF 방지값입니다. 프론트가 보관할 필요 없이 콜백 쿼리의 값을 그대로 넘기면 됩니다.

| errorCode | 상황 |
| --- | --- |
| `AU_018` | 소셜 인증 실패 (code 무효, state 만료) |
| `AU_019` | 이미 다른 계정에 연동된 소셜 계정 |
| `AU_020` | 가입 티켓 만료 (30분) → `authorize` 부터 다시 |

소셜 로그인 진입점은 프리랜서 화면에만 두세요. 클라이언트 소셜 로그인을 막는 `AU_023` 은 현재 서버가 던지지 않습니다.

---

## 6. 아이디(이메일) 찾기

```http
POST /api/v1/auth/find-email

{ "name": "홍길동", "phone": "01012345678" }
```

```jsonc
// 200
"data": {
  "accounts": [
    { "role": "CLIENT",     "maskedEmail": "ow****@pairing.com" },
    { "role": "FREELANCER", "maskedEmail": "us****@pairing.com" }
  ]
}
```

> **결과가 2건 나올 수 있습니다.** 같은 사람이 두 역할로 가입한 경우입니다.
> 목록으로 보여주고, 각 항목에서 해당 역할의 로그인 탭으로 이동시켜 주세요. 첫 번째 것만 쓰면 안 됩니다.

일치하는 계정이 없으면 `AU_024` (404). 이메일 전체 값은 어떤 경우에도 내려가지 않습니다.

---

## 7. 비밀번호 찾기 — 이메일 링크 (자세히)

이 절이 가장 실수가 잦습니다. **인증코드 방식이 아니라 메일에 담긴 링크를 타는 방식**입니다.

### 7-1. 전체 흐름

```
┌ 프론트: /find-password ──────────────────────────────────────────────┐
│ 사용자가 이메일 · 역할 · 이름 · 휴대폰 입력 후 "인증 링크 받기"       │
│   POST /api/v1/auth/password/reset-requests                          │
│   → 항상 200. 성공 문구를 보여주고 "메일함 확인" 안내 화면으로 전환   │
└──────────────────────────────────────────────────────────────────────┘
                              │
                    서버가 메일 발송 (토큰 3분)
                              │
┌ 사용자 메일함 ───────────────────────────────────────────────────────┐
│ [페어링] 비밀번호 재설정 안내                                         │
│   아래 링크로 3분 안에 접속하면 임시 비밀번호를 발급해 드립니다.       │
│   http://localhost:17000/reset-password?token=9f2c1e5a-....          │
└──────────────────────────────────────────────────────────────────────┘
                              │  클릭
┌ 프론트: /reset-password?token=... ───────────────────────────────────┐
│ 토큰을 쿼리에서 꺼내 화면에 보관                                      │
│ "임시 비밀번호 받기" 버튼을 사용자가 누르면                           │
│   POST /api/v1/auth/password/reset-confirm  { token }                │
│   → 200: 임시 비밀번호가 메일로 발송됨 → 로그인 화면으로              │
│   → AU_027: 링크 만료·사용됨 → 재요청 안내                           │
└──────────────────────────────────────────────────────────────────────┘
                              │
┌ 로그인 → tempPassword: true → 비밀번호 변경 화면 (강제) ─────────────┐
│   PATCH /api/v1/auth/password                                        │
└──────────────────────────────────────────────────────────────────────┘
```

### 7-2. 1단계 — 재설정 요청

```http
POST /api/v1/auth/password/reset-requests

{ "email": "user@pairing.com", "role": "FREELANCER", "name": "홍길동", "phone": "01012345678" }
```

> **입력이 틀려도 200 이 옵니다.** 존재하지 않는 이메일에 404 를 주면 "그 이메일은 가입되어 있다"를 알려주는 꼴이라
> 일부러 성공/실패를 구분하지 않습니다.
>
> 그래서 프론트는 **응답으로 성공 여부를 판단할 수 없습니다.** 항상
> "입력하신 정보가 일치하면 메일을 보내드렸습니다. 메일함을 확인해 주세요." 로 안내하세요.
> "메일을 보냈습니다" 라고 단정하면 사용자가 오지 않는 메일을 기다리게 됩니다.

소셜 전용 계정(비밀번호가 없는 계정)도 같은 이유로 조용히 무시됩니다.

### 7-3. 메일 링크 형식

```
{APP_FRONT_BASE_URL}/reset-password?token={UUID}
```

- `APP_FRONT_BASE_URL` 은 **서버 환경변수**입니다. 프론트가 정하는 값이 아닙니다.
  로컬 기본값이 `http://localhost:17000` 이라, 프론트를 17000 이 아닌 포트로 띄우면 링크가 깨집니다.
  포트를 바꿨다면 백엔드 담당자에게 `APP_FRONT_BASE_URL` 변경을 요청하세요.
- 프론트에 **`/reset-password` 라우트가 반드시 있어야 합니다.** 이 경로는 비로그인 상태로 열립니다.
- 토큰은 UUID 문자열이며 **3분** 유효하고 **1회용**입니다.

### 7-4. 2단계 — 링크 페이지 구현

```http
POST /api/v1/auth/password/reset-confirm

{ "token": "9f2c1e5a-3b44-4c21-9e0d-77a1b2c3d4e5" }
```

성공하면 `data: null` 이고, **임시 비밀번호가 메일로 한 번 더 발송**됩니다. 화면에는 임시 비밀번호가 내려오지 않습니다.

이 단계에서 세 가지 함정이 있습니다.

#### ① 페이지 진입만으로 자동 호출하면 안 됩니다

`useEffect` 에서 바로 호출하고 싶겠지만, 그러면 토큰이 사용자 의사와 무관하게 소모됩니다.

- 일부 메일 서비스·보안 솔루션이 **링크를 미리 열어 봅니다(프리페치).** 사용자가 클릭하기 전에 토큰이 소모될 수 있습니다.
- React StrictMode 개발 모드는 `useEffect` 를 **두 번** 실행합니다. 첫 호출이 성공하고 두 번째가 `AU_027` 로 실패해,
  개발 중에만 "만료된 링크" 화면이 뜹니다.

**사용자가 버튼을 눌러야 호출되게 하세요.**

```tsx
// /reset-password
export default function ResetPasswordPage() {
  const token = new URLSearchParams(useLocation().search).get('token');
  const [state, setState] = useState<'idle' | 'loading' | 'done' | 'invalid'>('idle');
  const submitted = useRef(false);            // 더블클릭 방어

  if (!token) return <Invalid message="잘못된 접근입니다. 비밀번호 찾기를 다시 진행해 주세요." />;

  const submit = async () => {
    if (submitted.current) return;
    submitted.current = true;
    setState('loading');
    try {
      await api.post('/api/v1/auth/password/reset-confirm', { token });
      setState('done');
    } catch (e) {
      const code = e.response?.data?.errorCode;
      setState(code === 'AU_027' ? 'invalid' : 'idle');
      submitted.current = false;               // 재시도 가능한 오류만 잠금 해제
    }
  };

  if (state === 'done')    return <Done />;    // "임시 비밀번호를 메일로 보냈습니다" + 로그인 이동
  if (state === 'invalid') return <Expired />; // 만료 안내 + 비밀번호 찾기 재시작 링크
  return <button onClick={submit} disabled={state === 'loading'}>임시 비밀번호 받기</button>;
}
```

#### ② 토큰은 1회용입니다

성공하면 서버가 토큰을 즉시 삭제합니다. 같은 링크를 다시 열면 `AU_027` 입니다.
"완료" 화면에서 뒤로가기를 눌러 다시 제출하는 경로를 막아 두세요.

#### ③ 성공 시점에 기존 로그인이 전부 끊깁니다

임시 비밀번호가 발급되면 서버가 해당 계정의 토큰과 세션을 모두 지웁니다.
다른 탭에서 로그인 중이었다면 그 탭은 다음 요청에서 401 을 받습니다. 정상 동작입니다.

#### 실패 코드

| errorCode | HTTP | 상황 | 화면 처리 |
| --- | --- | --- | --- |
| `AU_027` | 400 | 링크 만료(3분 초과) 또는 이미 사용됨 | "링크가 만료되었습니다. 다시 요청해 주세요." + `/find-password` 이동 버튼 |
| `AU_026` | 500 | 임시 비밀번호 메일 발송 실패 | "잠시 후 다시 시도해 주세요." **이 경우 토큰은 이미 소모됐을 수 있으니 재요청을 안내** |
| `GLOBAL_002` | 400 | `token` 이 비었음 | 잘못된 접근 화면 |

### 7-5. 3단계 — 임시 비밀번호로 로그인한 뒤

메일로 받은 임시 비밀번호로 로그인하면 `tempPassword: true` 가 옵니다. **변경 화면으로 강제 이동**시켜야 합니다.
(5-1 의 주의 참고 — 서버가 막아 주지 않으므로 프론트 라우터 가드로 처리합니다.)

변경 화면은 **8절(마이페이지 비밀번호 변경)과 완전히 같은 화면**입니다.
임시 비밀번호를 입력받지 않고, 이메일 인증코드를 받아 새 비밀번호를 등록합니다.
이미 로그인한 상태이므로 8절 절차를 그대로 따르면 됩니다.

---

## 8. 비밀번호 변경 (마이페이지)

로그인한 사용자가 마이페이지에서 비밀번호를 바꾸는 흐름입니다.
**현재 비밀번호를 묻지 않습니다.** 이메일 인증코드로 본인을 확인한 뒤 새 비밀번호를 입력받습니다.

7절(로그인 전 비밀번호 찾기)과 헷갈리기 쉬운데, 완전히 다른 흐름입니다.

| | 로그인 전 · 비밀번호 찾기 (7절) | 로그인 후 · 비밀번호 변경 (8절) |
| --- | --- | --- |
| 본인 확인 | 메일 **링크** | 메일 **인증코드** |
| 결과 | 임시 비밀번호를 메일로 발급 | 사용자가 새 비밀번호를 직접 입력 |
| 인증 정보 | 이메일·역할·이름·휴대폰을 폼으로 입력 | 로그인 세션에서 계정을 특정 (이메일을 믿지 않음) |

### 8-1. 흐름

```
[1] "인증코드 받기" 버튼
      POST /api/v1/auth/email-verifications
      { "email": "<내 이메일>", "purpose": "PASSWORD_CHANGE" }
      → data: { expiresAt, remainingSendCount }   ← 3분 타이머 시작

[2] 코드 입력 후 "확인"
      POST /api/v1/auth/email-verifications/confirm
      { "email": "<내 이메일>", "purpose": "PASSWORD_CHANGE", "code": "482913" }
      → 200 이면 새 비밀번호 입력칸을 연다 (인증 표시가 30분간 유지된다)

[3] 새 비밀번호 + 확인 입력 후 "변경"
      PATCH /api/v1/auth/password
      { "newPassword": "Pairing!2026", "newPasswordConfirm": "Pairing!2026" }
      → 200 이면 모든 세션이 끊긴다 → 로그인 화면으로
```

`<내 이메일>` 은 `GET /auth/me` 의 `email` 을 씁니다. 사용자가 직접 입력하게 하지 마세요.
3단계에서 서버는 **세션의 계정에 저장된 이메일**로 인증 여부를 확인하므로, 다른 이메일로 인증해도 통과하지 않습니다.

### 8-2. 요청 · 응답

```http
PATCH /api/v1/auth/password
Content-Type: application/json

{ "newPassword": "Pairing!2026", "newPasswordConfirm": "Pairing!2026" }
```

성공 시 `data: null` 이고 `accessToken` / `refreshToken` 쿠키가 만료 처리됩니다.

> **성공 응답을 받으면 즉시 로그인 화면으로 보내세요.** 세션이 끊겨 있어서 홈으로 보내면 바로 401 이 납니다.
> "비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요." 를 안내합니다.

### 8-3. 실패 코드

| errorCode | HTTP | 상황 | 화면 처리 |
| --- | --- | --- | --- |
| `AU_006` | 400 | 인증코드 확인을 안 했거나 30분이 지남 | 1단계(인증코드 받기)로 되돌린다 |
| `AU_010` | 400 | 비밀번호 형식 위반 (대소문자+숫자+특수문자, 8~20자) | 입력칸 아래 에러 |
| `AU_011` | 400 | 새 비밀번호 확인 불일치 | 입력칸 아래 에러 |
| `AU_028` | 400 | 기존 비밀번호와 동일 | "현재와 다른 비밀번호를 입력해 주세요" |
| `AU_029` | 400 | 소셜 전용 계정 | 애초에 메뉴를 숨긴다 |

인증 표시는 **1회용**입니다. 변경에 성공하면 지워지므로, 한 번 더 바꾸려면 인증부터 다시 해야 합니다.
반대로 3단계가 `AU_010` 같은 형식 오류로 실패했을 때는 표시가 남아 있으니 인증을 다시 받을 필요 없습니다.

---

## 9. 계정 잠금 해제

로그인 5회 연속 실패 시 `AU_002`(423) 가 나고 계정이 잠깁니다. 이메일 인증으로 풉니다.

```http
[1] POST /api/v1/auth/email-verifications   { "email": "...", "purpose": "UNLOCK" }
[2] POST /api/v1/auth/unlock                { "email": "...", "role": "FREELANCER", "code": "482913" }
```

> **`/auth/email-verifications/confirm` 을 거치지 않습니다.** 받은 코드를 `/auth/unlock` 에 바로 넣으면
> 서버가 안에서 검증하고 잠금을 풉니다. confirm 을 먼저 부르면 코드가 소모돼 `/unlock` 이 실패합니다.

코드 오류는 3-2 표와 같습니다(`AU_004` / `AU_005` / `AU_012`). 계정을 못 찾으면 `AU_024`.
성공 후에는 로그인 화면으로 보내면 됩니다.

---

## 10. 에러 코드 전체 표

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| `GLOBAL_002` | 400 | 입력 형식 검증 실패 |
| `GLOBAL_006` | 401 | 인증 필요 (토큰 없음) |
| `GLOBAL_009` | 401 | Access 토큰 만료 → refresh |
| `GLOBAL_010` | 401 | 토큰 위조·손상 → 재로그인 |
| `GLOBAL_011` | 401 | 다른 기기 로그인으로 세션 종료 |
| `AU_001` | 401 | ID/PW 불일치 (사유 미구분) |
| `AU_002` | 423 | 5회 실패로 계정 잠금 |
| `AU_003` | 429 | 이메일 발송 1시간 15회 초과 |
| `AU_004` / `AU_005` / `AU_012` | 400 | 코드 불일치 / 만료 / 시도 초과 |
| `AU_006` | 400 | 이메일 인증 미완료 (또는 30분 초과) |
| `AU_007` / `AU_008` / `AU_009` | 409 | 이메일 / 휴대폰 / 사업자번호 중복 |
| `AU_010` / `AU_011` | 400 | 비밀번호 형식 / 확인 불일치 |
| `AU_013` | 400 | 약관 동의 목록 누락 |
| `AU_014` | 429 | IP 차단 (1시간 20회 실패 → 2시간) |
| `AU_015` | 401 | 재발급 중 다른 기기 로그인 감지 |
| `AU_016` | 401 | 리프레시 토큰 없음·무효 |
| `AU_017` | 403 | 임시 비밀번호 상태 — **정의만 되어 있고 현재 서버가 던지지 않는다** |
| `AU_018` / `AU_019` / `AU_020` | 400 / 409 / 400 | 소셜 인증 실패 / 이미 연동됨 / 티켓 만료 |
| `AU_021` / `AU_022` | 403 | 재가입 제한 / 정지 계정 |
| `AU_023` | 400 | 클라이언트 소셜 로그인 시도 — **정의만 되어 있고 현재 서버가 던지지 않는다** |
| `AU_024` | 404 | 계정을 찾을 수 없음 |
| `AU_025` | 400 | 만 18세 미만 |
| `AU_026` | 500 | 메일 발송 실패 |
| `AU_027` | 400 | 비밀번호 재설정 링크 무효·만료 |
| `AU_028` | 400 | 새 비밀번호가 기존과 동일 |
| `AU_029` | 400 | 소셜 전용 계정이라 비밀번호 변경 불가 |
| `AC_006` | 400 | 지원하지 않는 은행 코드 |
| `TM_002` / `TM_003` | 400 | 필수 약관 미동의 / 알 수 없는 약관 포함 |

---

## 11. 구현 체크리스트

- [ ] axios/fetch 인스턴스에 `withCredentials` / `credentials: 'include'` 를 넣었다
- [ ] 401 처리를 인터셉터 **한 곳**에 모았고, `GLOBAL_009` 만 refresh 후 재시도한다
- [ ] refresh 동시 호출을 막는 잠금(Promise 재사용)을 넣었다
- [ ] 로그인 화면에 클라이언트 / 프리랜서 탭이 있고 `role` 을 보낸다
- [ ] `tempPassword: true` 면 비밀번호 변경 화면으로 강제 이동시킨다 (서버가 막아 주지 않으므로 라우터 가드 필수)
- [ ] 인증코드 타이머를 `expiresAt` 기준으로 그린다 (로컬 3분 타이머 아님)
- [ ] 약관 `termsId` 를 하드코딩하지 않고 `GET /terms` 응답을 쓴다
- [ ] 마케팅 미동의도 `agreed: false` 로 함께 보낸다 (항목을 빼지 않는다)
- [ ] 개인정보 처리방침을 가입 동의 체크박스에 넣지 않는다 (`/terms/documents` 로 열람만)
- [ ] `/reset-password` 라우트를 만들었고 **버튼 클릭으로만** confirm 을 호출한다
- [ ] 비밀번호 찾기 요청 결과를 "일치하면 발송했습니다" 로 안내한다 (성공 단정 금지)
- [ ] 마이페이지 비밀번호 변경은 인증코드 → 확인 → 새 비밀번호 3단계로 만든다 (현재 비밀번호를 묻지 않는다)
- [ ] 비밀번호 변경 성공 후 로그인 화면으로 보낸다 (홈으로 보내면 401)
- [ ] 아이디 찾기 결과를 목록으로 처리한다 (2건 가능)
- [ ] 서버 `APP_FRONT_BASE_URL` / `CORS_ALLOWED_ORIGINS` 가 실제 프론트 주소와 일치한다

---

## 참고

- 전체 API 계약: [.ai/API.md](../.ai/API.md)
- 엔드포인트 목록(CSV): [api-spec.csv](api-spec.csv)
- DTO 필드 사전(CSV): [api-dto.csv](api-dto.csv)
- 실행 중인 서버의 Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
