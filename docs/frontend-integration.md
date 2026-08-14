# 프론트엔드 연동 가이드 (회원가입 / 로그인 / 소셜로그인)

이 문서 하나만 보고 연동할 수 있게 정리했습니다. 요청·응답 예시는 실제 구현 기준입니다.

- 서버: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 엔드포인트 요약본: [.ai/API.md](../.ai/API.md) · 설계 배경: [docs/design/auth-design.md](design/auth-design.md)

---

## 0. 시작하기 전에

### 0-1. 쿠키 설정이 제일 중요합니다

토큰은 응답 본문에 오지 않습니다. **HttpOnly 쿠키(`accessToken`, `refreshToken`)로만** 내려갑니다.
JS에서 읽을 수 없고, 읽을 필요도 없습니다. 대신 **모든 요청에 자격증명을 실어야** 합니다.

```js
// fetch
fetch(`${API_BASE}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',   // 이거 빠뜨리면 로그인해도 다음 요청이 401
  body: JSON.stringify({ email, password, role }),
});

// axios
const api = axios.create({ baseURL: API_BASE, withCredentials: true });
```

서버에는 프론트 오리진이 등록돼 있어야 합니다. 로컬 기본값은 `http://localhost:17000`, `http://127.0.0.1:17000`이고
다른 포트를 쓰면 백엔드에 `CORS_ALLOWED_ORIGINS` 추가를 요청하세요. (와일드카드 `*`는 쿠키 방식과 함께 쓸 수 없습니다)

### 0-2. 공통 응답 형식

성공:

```json
{
  "timestamp": "2026-08-05T12:34:56Z",
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": { }
}
```

실패:

```json
{
  "timestamp": "2026-08-05T12:34:56Z",
  "status": 401,
  "errorCode": "AU_001",
  "message": "ID나 PW가 일치하지 않습니다.",
  "traceId": "a1b2c3d4"
}
```

- 성공은 `data`, 실패는 `errorCode` + `message`를 보면 됩니다. 실패 응답에는 `data`가 없습니다.
- **분기는 `errorCode`로 하고, 사용자에게 보여줄 문구는 `message`를 그대로 쓰면 됩니다.** 서버 메시지가 이미 사용자용입니다.
- `traceId`는 백엔드에 문의할 때 같이 알려주세요. 로그를 그 ID로 찾습니다.
- 입력값 검증 실패(400 `GLOBAL_002`)는 `message`에 `"phone: 전화번호 형식이 올바르지 않습니다."`처럼 필드명이 붙어서 옵니다.

### 0-3. 공통 fetch 래퍼 예시

```ts
type ApiSuccess<T> = { timestamp: string; status: number; code: string; message: string; data: T };
type ApiError = { timestamp: string; status: number; errorCode: string; message: string; traceId: string };

export class ApiException extends Error {
  constructor(public readonly errorCode: string, message: string, public readonly status: number) {
    super(message);
  }
}

let refreshing: Promise<void> | null = null;

export async function apiCall<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });

  const body = await res.json();

  if (res.ok) return (body as ApiSuccess<T>).data;

  const error = body as ApiError;

  // 액세스 토큰 만료 -> 한 번만 재발급하고 원래 요청 재시도
  if (error.errorCode === 'GLOBAL_009' && retry) {
    refreshing ??= apiCall<unknown>('/api/v1/auth/refresh', { method: 'POST' }, false)
      .then(() => undefined)
      .finally(() => { refreshing = null; });
    await refreshing;
    return apiCall<T>(path, init, false);
  }

  // 다른 기기에서 로그인 -> 모달 띄우고 로그인 페이지로
  if (error.errorCode === 'GLOBAL_011' || error.errorCode === 'AU_015') {
    showSessionTerminatedModal();
  }

  throw new ApiException(error.errorCode, error.message, res.status);
}
```

### 0-4. 반드시 처리해야 하는 3가지 상태

| 상황 | 신호 | 화면 처리 |
| --- | --- | --- |
| 액세스 토큰 만료 | 401 `GLOBAL_009` | `/auth/refresh` 호출 후 원래 요청 재시도. 실패하면 로그인 페이지 |
| 다른 기기 로그인 | 401 `GLOBAL_011` (일반 요청) / `AU_015` (refresh 시) | **확인 버튼 있는 모달** 후 메인 또는 로그인 페이지 |
| 임시 비밀번호 상태 | 로그인 응답 `data.tempPassword === true` | 비밀번호 변경 화면으로 강제 이동 |

---

## 1. 화면별 호출 순서

### 1-1. 클라이언트 회원가입

```
[가입 폼 진입]
  GET  /api/v1/meta/business-fields        사업 분야 셀렉트
  GET  /api/v1/meta/employee-counts        직원수 셀렉트
  GET  /api/v1/meta/banks                  은행 셀렉트
  GET  /api/v1/meta/card-companies         카드사 셀렉트
  GET  /api/v1/terms?role=CLIENT           약관 목록

[입력 중 - blur 시점]
  GET  /api/v1/auth/exists/email?email=...&role=CLIENT
  GET  /api/v1/auth/exists/phone?phone=...&role=CLIENT
  GET  /api/v1/auth/exists/business-no?businessNo=...

[이메일 인증]
  POST /api/v1/auth/email-verifications          -> expiresAt 으로 3분 타이머
  POST /api/v1/auth/email-verifications/confirm  -> 성공하면 30분 안에 제출

[제출]
  POST /api/v1/auth/signup/client   -> 201, 로그인 페이지로
```

> 카드와 계좌는 가입 폼에서 함께 받습니다. 수수료는 카드로 결제하고 용역비는 계좌로 받기 때문에 둘 다 필수입니다.

### 1-2. 프리랜서 일반 회원가입

`business-fields` / `employee-counts` / `business-no` 중복확인이 빠지고, `role=FREELANCER`로 바뀝니다.
`birthDate`(만 18세 이상)가 추가됩니다.

```
GET  /api/v1/terms?role=FREELANCER
GET  /api/v1/auth/exists/email?email=...&role=FREELANCER
POST /api/v1/auth/email-verifications        (purpose: SIGNUP)
POST /api/v1/auth/email-verifications/confirm
POST /api/v1/auth/signup/freelancer          -> 201
```

### 1-3. 프리랜서 소셜 회원가입 / 로그인

```
[소셜 버튼 클릭]
  GET  /api/v1/auth/social/kakao/authorize?returnUrl=/resume
       -> data.authorizeUrl 로 window.location.href 이동

[공급자 로그인 후 프론트 콜백 라우트(/oauth/callback/kakao)에서 code, state 수신]
  POST /api/v1/auth/social/kakao/callback  { code, state }

       data.status === 'LOGIN'
         -> 이미 쿠키 발급 완료. 로그인 상태로 원래 화면 복귀

       data.status === 'SIGNUP_REQUIRED'
         -> data.signUpTicket 보관, data.email(수정 불가) / data.name(수정 가능) 프리필
         -> 추가 정보 입력(이름/전화번호/생년월일/카드/계좌) + 약관 동의
  POST /api/v1/auth/signup/freelancer/social  -> 201, 쿠키까지 함께 발급됨(바로 로그인 상태)
```

- **소셜은 프리랜서 전용입니다.** 클라이언트 탭에는 소셜 버튼을 노출하지 마세요.
- 소셜 가입에는 이메일 인증 단계가 없습니다.
- `signUpTicket` 유효시간은 30분입니다. 만료되면 `AU_020`이 오고 소셜 인증부터 다시 해야 합니다.
- 이메일 입력칸은 **disabled**로 두세요. 서버는 요청 본문의 email을 아예 받지 않습니다.

### 1-4. 로그인

```
POST /api/v1/auth/login  { email, password, role }
  -> 쿠키 2종 자동 저장
  -> data.tempPassword === true 이면 비밀번호 변경 화면
  -> 아니면 직전 화면으로 복귀 (프론트가 보관한 returnUrl)
```

### 1-5. 아이디 / 비밀번호 찾기

```
[아이디 찾기]
  POST /api/v1/auth/find-email  { name, phone }
    -> data.accounts = [{ role, maskedEmail }]  (두 역할로 가입했으면 2건)

[비밀번호 찾기]
  POST /api/v1/auth/password/reset-requests  { email, role, name, phone }
    -> 항상 200. "메일을 보냈다"는 안내만 표시
  [메일 링크] {FRONT_BASE_URL}/reset-password?token=xxx 로 진입
  POST /api/v1/auth/password/reset-confirm   { token }
    -> 임시 비밀번호가 메일로 발송됨
  로그인 -> tempPassword=true
  PATCH /api/v1/auth/password  { currentPassword, newPassword, newPasswordConfirm }
    -> 세션이 끊기므로 로그인 페이지로 이동 후 재로그인

[계정 잠금 해제]  (로그인에서 AU_002를 받은 경우)
  POST /api/v1/auth/email-verifications  { email, purpose: 'UNLOCK' }
  POST /api/v1/auth/unlock               { email, role, code }
    -> 잠금 해제. 다시 로그인
```

---

## 2. 엔드포인트 상세

인증 열의 `O`는 로그인 필요(쿠키 자동 전송), `X`는 비로그인 호출 가능입니다.

### 2-1. 선택 목록 / 약관

#### `GET /api/v1/meta/business-fields` (X)

```json
{ "code": "BUSINESS_FIELDS_FOUND",
  "data": [ { "code": "IT_CONTENTS_AI", "label": "IT/컨텐츠/AI" }, { "code": "GAME", "label": "게임" } ] }
```

`code`를 가입 요청에 그대로 넣고, `label`을 화면에 표시합니다. 20개 항목이 옵니다.

#### `GET /api/v1/meta/employee-counts` (X)

`SIZE_1_4`(1~4명) / `SIZE_5_9` / `SIZE_10_49` / `SIZE_50_299` / `SIZE_300_OVER`(300명 이상)

#### `GET /api/v1/meta/banks` (X)

```json
{ "code": "BANKS_FOUND",
  "data": [ { "code": "004", "label": "KB국민은행" }, { "code": "088", "label": "신한은행" } ] }
```

계좌 등록 셀렉트에 씁니다. `code`(금융결제원 기관코드)를 `bankAccount.bankCode`로 그대로 보냅니다.

#### `GET /api/v1/meta/card-companies` (X)

```json
{ "code": "CARD_COMPANIES_FOUND",
  "data": [ { "code": "BC", "label": "BC카드" }, { "code": "SHINHAN", "label": "신한카드" } ] }
```

카드 등록 셀렉트에 씁니다. `code`를 `card.cardBrand`로 그대로 보냅니다.

**은행과 달리 숫자 코드가 아닙니다.** 카드사는 금융결제원 기관코드 같은 외부 규격이 없어서 `SHINHAN` 처럼
영문 코드가 내려갑니다. `"신한카드"`(한글명)를 보내면 400입니다. `label`은 화면 표시 전용입니다.

#### `GET /api/v1/terms?role=CLIENT` (X)

`role`은 `CLIENT` 또는 `FREELANCER`입니다.

```json
{ "code": "TERMS_FOUND",
  "data": [
    { "termsId": 1, "code": "SERVICE_CLIENT", "title": "클라이언트 서비스 이용약관",
      "version": "v1.0", "required": true, "content": "..." },
    { "termsId": 6, "code": "MARKETING", "title": "마케팅 정보 수신 동의",
      "version": "v1.0", "required": false, "content": "..." }
  ] }
```

- `required: true`인 항목은 전부 체크해야 다음으로 넘어갈 수 있습니다.
- 가입 요청의 `agreements`에는 **화면에 보여준 모든 약관**을 `{termsId, agreed}` 형태로 담아 보내세요.
  선택 약관을 체크 안 했으면 `agreed: false`로 보냅니다. 목록에 없는 `termsId`를 보내면 `TM_003`입니다.

### 2-2. 이메일 인증

#### `POST /api/v1/auth/email-verifications` (X)

```json
// 요청
{ "email": "owner@pairing.com", "purpose": "SIGNUP" }

// 응답
{ "code": "VERIFICATION_CODE_SENT",
  "data": { "expiresAt": "2026-08-05T12:37:56", "remainingSendCount": 14 } }
```

- `purpose`: `SIGNUP`(가입) / `UNLOCK`(잠금 해제) / `PROFILE_UPDATE`(정보 변경)
- `expiresAt`까지 3분 타이머를 표시합니다. 만료되면 재발송해야 하고, 이전 코드는 무효입니다.
- 버튼은 첫 클릭 후 "재발송"으로 바꾸고, 연타를 막아 주세요.
- 1시간에 15회까지입니다. 초과하면 `AU_003`(429) — "현재 시각 기준 1시간 뒤에 다시 시도" 안내를 띄우세요.
  남은 횟수는 `remainingSendCount`로 미리 보여줄 수 있습니다.

#### `POST /api/v1/auth/email-verifications/confirm` (X)

```json
{ "email": "owner@pairing.com", "purpose": "SIGNUP", "code": "123456" }
```

성공 시 `data`는 `null`입니다. **확인 성공 후 30분 안에 가입을 제출해야 합니다.** 넘기면 `AU_006`.
코드 입력은 5회까지고, 초과하면 `AU_012` — 재발송부터 다시 해야 합니다.

### 2-3. 중복 확인

| 경로 | 파라미터 |
| --- | --- |
| `GET /api/v1/auth/exists/email` | `email`, `role` |
| `GET /api/v1/auth/exists/phone` | `phone`, `role` |
| `GET /api/v1/auth/exists/business-no` | `businessNo` (하이픈 없이 10자리) |

```json
{ "code": "EMAIL_CHECKED", "data": { "duplicated": false } }
```

> **이메일·휴대폰은 역할별로 판정합니다.** 같은 사람이 클라이언트 계정과 프리랜서 계정을 각각 가질 수 있어서,
> `role`을 반드시 함께 보내야 합니다. 클라이언트로 쓰인 이메일이어도 프리랜서로는 가입할 수 있습니다.
> 같은 역할 안에서는 소셜↔일반을 포함해 중복이 불가합니다.

### 2-4. 회원가입

#### `POST /api/v1/auth/signup/client` (X) → 201

```json
{
  "companyName": "주식회사 페어링",
  "businessNo": "1234567890",
  "businessField": "IT_CONTENTS_AI",
  "employeeCount": "SIZE_10_49",
  "email": "owner@pairing.com",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "password": "Passw0rd!",
  "passwordConfirm": "Passw0rd!",
  "address": {
    "sido": "서울",
    "sigungu": "강남구",
    "roadAddress": "서울 강남구 테헤란로 123",
    "addressDetail": "10층 1002호",
    "zipCode": "06234"
  },
  "card": { "cardNumber": "1234-5678-1234-5678", "cardBrand": "SHINHAN" },
  "bankAccount": { "bankCode": "088", "accountNo": "110-123-456789", "accountHolder": "홍길동" },
  "agreements": [
    { "termsId": 1, "agreed": true },
    { "termsId": 3, "agreed": true },
    { "termsId": 6, "agreed": false }
  ]
}
```

```json
{ "status": 201, "code": "CLIENT_SIGNED_UP", "data": { "accountId": 1, "role": "CLIENT" } }
```

#### `POST /api/v1/auth/signup/freelancer` (X) → 201

```json
{
  "name": "홍길동",
  "phone": "010-1234-5678",
  "email": "user@pairing.com",
  "password": "Passw0rd!",
  "passwordConfirm": "Passw0rd!",
  "birthDate": "1995-03-01",
  "address": { /* 위와 동일 */ },
  "card": { /* 위와 동일 */ },
  "bankAccount": { /* 위와 동일 */ },
  "agreements": [ /* 위와 동일 */ ]
}
```

응답 `code`는 `FREELANCER_SIGNED_UP`, `data`는 `{ accountId, role: "FREELANCER" }`.

#### `POST /api/v1/auth/signup/freelancer/social` (X) → 201 + 쿠키

```json
{
  "signUpTicket": "8f1c...",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "birthDate": "1995-03-01",
  "address": { /* 동일 */ },
  "card": { /* 동일 */ },
  "bankAccount": { /* 동일 */ },
  "agreements": [ /* 동일 */ ]
}
```

```json
{ "status": 201, "code": "FREELANCER_SIGNED_UP",
  "data": { "accountId": 7, "role": "FREELANCER", "name": "홍길동", "tempPassword": false } }
```

가입과 동시에 로그인 쿠키가 내려옵니다. 별도 로그인 호출이 필요 없습니다.

**공통 규칙**

- `card`와 `bankAccount`는 세 가입 경로 모두 **필수**입니다. 카드번호·계좌번호는 하이픈·공백을 넣어도 되고, 서버가 숫자만 남겨 암호화 저장합니다. 조회 시에는 카드 끝 4자리만 나갑니다.
- **`cardNumber`는 숫자 16자리(4자리씩 4묶음)입니다.** `1234-5678-1234-5678`, `1234 5678 1234 5678`, `1234567812345678` 모두 받습니다. 자릿수가 다르면 400입니다.
- **`accountNo`는 숫자 10~14자리입니다.** 은행마다 자릿수가 달라 범위로 받습니다. 하이픈·공백은 허용하지만 맨 앞·맨 뒤 하이픈이나 연속된 하이픈은 400입니다.
- `cardBrand`는 `GET /api/v1/meta/card-companies` 응답의 `code`를 그대로 보냅니다. 한글 카드사명이 아니라 `SHINHAN` 같은 enum 이름입니다. 목록에 없는 값이면 400입니다.
- `bankCode`는 `GET /api/v1/meta/banks` 응답의 `code`를 그대로 보냅니다. 목록에 없는 값이면 `AC_006`입니다.
- **`address`는 세 가입 경로 모두 필수입니다.** 프리랜서 가입도 2026-08-14 부터 주소를 받습니다(그 전에는 가입 후 마이페이지에서만 채울 수 있었습니다).
- 주소는 **주소 찾기 위젯(다음·카카오 우편번호) 결과를 합치지 말고 조각째** 보냅니다. 합쳐 보내면 수정 화면에서 다시 나눌 수 없습니다. 사용자가 직접 쓰는 칸은 `addressDetail` 하나입니다.
- `sido`와 `roadAddress`는 필수, `sigungu`·`addressDetail`·`zipCode`는 선택입니다. **`sigungu`가 선택인 이유는 세종특별자치시에 시·군·구가 없어서**입니다 — 필수로 두면 세종시 사용자가 가입할 수 없습니다.
- 서버는 형식(길이·필수)만 봅니다. 시·군·구 코드표를 들고 있지 않아 "실존하는 지역인가"는 검증하지 않습니다.
- 조회 응답에는 한 줄로 합친 `address`와 나눠 담은 `addressParts`가 **둘 다** 나갑니다. 화면 표시는 `address`, 수정 폼은 `addressParts`를 쓰세요.
- `phone`·`businessNo`·`bankAccount.bankCode` 는 **필수**입니다. 빠뜨리면 400 `GLOBAL_002` 로 어느 칸이 비었는지 함께 옵니다.
- `phone`은 하이픈이 있어도 없어도 됩니다. 서버가 숫자만 남겨 저장합니다.
- `businessNo`는 하이픈 없이 숫자 10자리만 허용합니다. (국세청 진위확인은 아직 연동 전이라 형식·중복만 봅니다)

### 2-5. 로그인 / 세션

#### `POST /api/v1/auth/login` (X)

```json
// 요청 — role 필수
{ "email": "user@pairing.com", "password": "Passw0rd!", "role": "FREELANCER" }

// 응답 + Set-Cookie: accessToken, refreshToken
{ "code": "LOGIN_SUCCESS",
  "data": { "accountId": 7, "role": "FREELANCER", "name": "홍길동", "tempPassword": false } }
```

- 탭(클라이언트/프리랜서)에서 고른 값을 `role`로 보냅니다. 탭을 잘못 고르면 `AU_001`입니다.
- 실패 사유는 구분되지 않습니다. `AU_001`이면 화면에는 "ID나 PW가 일치하지 않습니다."만 보여주세요.
- **로그인하면 그 계정의 다른 기기 세션은 즉시 끊깁니다.**

#### `POST /api/v1/auth/refresh` (쿠키)

액세스 토큰(1시간) 만료 시 호출합니다. 리프레시 토큰(7일)도 함께 회전됩니다.

> **평소에는 부를 일이 거의 없습니다.** 액세스 토큰은 요청이 들어올 때마다 만료가 미뤄집니다(슬라이딩 세션).
> 남은 수명이 30분 아래인 요청에서 서버가 새 `accessToken` 쿠키를 내려주므로, **30분 안에 아무 API나
> 한 번이라도 부르면 세션이 유지됩니다.** HttpOnly 쿠키라 브라우저가 알아서 교체하고 프론트가 할 일은 없습니다.
>
> 이 API 가 필요한 경우는 **30분 넘게 아무것도 안 하다가 돌아온 상황**뿐입니다. 그때는 기존대로
> `GLOBAL_009` → `/auth/refresh` → 원래 요청 1회 재시도 흐름을 그대로 쓰면 됩니다.
응답 형식은 로그인과 같고 `code`는 `TOKEN_REISSUED`입니다.

#### `POST /api/v1/auth/logout` (쿠키)

Redis의 토큰·세션을 지우고 쿠키를 만료시킵니다. 만료된 토큰으로 호출해도 200이 옵니다. 이후 메인 페이지로 이동시키세요.

#### `GET /api/v1/auth/me` (O)

```json
{ "code": "ME_FOUND",
  "data": { "accountId": 7, "email": "user@pairing.com", "role": "FREELANCER",
            "name": "홍길동", "companyName": null, "tempPassword": false } }
```

클라이언트로 로그인하면 `companyName`이 함께 옵니다.

```json
{ "code": "ME_FOUND",
  "data": { "accountId": 3, "email": "owner@pairing.com", "role": "CLIENT",
            "name": "홍길동", "companyName": "주식회사 페어링", "tempPassword": false } }
```

새로고침 시 로그인 상태 복원에 쓰면 됩니다. 401이면 비로그인으로 처리하세요.

**화면에 찍는 이름은 역할마다 다릅니다.**

- `name`은 **담당자명**입니다. 클라이언트도 대표자 개인 이름이지 기업명이 아닙니다.
- 클라이언트의 **메인 페이지·프로필에는 `companyName`을 찍어야 합니다.** 기업 회원이라 화면에 개인 이름이 뜨면 안 됩니다.
- 프리랜서는 `companyName`이 항상 `null`이므로 `companyName ?? name`으로 두 역할을 함께 처리할 수 있습니다.
- 두 값을 하나로 합쳐 내리지 않은 이유는 **프로필 화면이 기업명과 담당자명을 동시에** 보여주기 때문입니다. `name`을 기업명으로 덮어쓰면 "담당자: OOO" 줄까지 기업명이 됩니다.
- 클라이언트 계정인데 기업 프로필이 없는 예외 데이터에서는 `companyName`이 `null`로 옵니다. 이 API는 로그인 상태 확인이 본업이라 프로필이 없다고 401/404를 내지 않습니다. 프론트에서 `name` 폴백을 두세요.

### 2-6. 소셜 로그인

#### `GET /api/v1/auth/social/{provider}/authorize?returnUrl=` (X)

`{provider}`는 `kakao` / `google` (소문자).

```json
{ "code": "AUTHORIZE_URL_ISSUED",
  "data": { "authorizeUrl": "https://kauth.kakao.com/oauth/authorize?...", "state": "3f2a..." } }
```

`authorizeUrl`로 이동시키면 됩니다. `state`는 서버가 검증하므로 프론트가 따로 보관할 필요는 없지만,
공급자가 콜백으로 돌려준 `state`를 그대로 다음 요청에 실어야 합니다.

#### `POST /api/v1/auth/social/{provider}/callback` (X)

```json
// 요청
{ "code": "공급자가 준 인가 코드", "state": "3f2a..." }

// 응답 A — 기존 회원 (쿠키 발급 완료)
{ "code": "SOCIAL_AUTH_PROCESSED",
  "data": { "status": "LOGIN",
            "login": { "accountId": 7, "role": "FREELANCER", "name": "홍길동", "tempPassword": false },
            "signUpTicket": null, "email": null, "name": null } }

// 응답 B — 신규 (가입 필요)
{ "code": "SOCIAL_AUTH_PROCESSED",
  "data": { "status": "SIGNUP_REQUIRED", "login": null,
            "signUpTicket": "8f1c...", "email": "user@gmail.com", "name": "홍길동" } }
```

`state`는 한 번만 쓸 수 있습니다. 새로고침으로 콜백을 두 번 호출하면 `AU_018`이 나므로,
콜백 처리 후 히스토리를 교체(`replace`)해 주세요.

### 2-7. 계정 복구

#### `POST /api/v1/auth/find-email` (X)

```json
// 요청
{ "name": "홍길동", "phone": "010-1234-5678" }

// 응답
{ "code": "EMAIL_FOUND",
  "data": { "accounts": [ { "role": "CLIENT", "maskedEmail": "ho*****@gmail.com" },
                          { "role": "FREELANCER", "maskedEmail": "ho*****@gmail.com" } ] } }
```

마스킹 규칙은 앞 2글자 공개 + 도메인 전체 공개입니다. 두 역할로 가입한 사람은 2건이 오므로,
어느 탭으로 로그인해야 하는지 함께 보여주세요. 일치하는 계정이 없으면 404 `AU_024`.

#### `POST /api/v1/auth/password/reset-requests` (X)

```json
{ "email": "user@pairing.com", "role": "FREELANCER", "name": "홍길동", "phone": "010-1234-5678" }
```

**정보가 틀려도 200이 옵니다.** (가입 여부가 노출되지 않도록) 화면에는 항상 "일치하는 계정이 있으면 메일을 보냈습니다"로 안내하세요.

#### `POST /api/v1/auth/password/reset-confirm` (X)

메일 링크(`{FRONT_BASE_URL}/reset-password?token=...`)로 들어온 페이지에서 토큰만 보내면 됩니다.

```json
{ "token": "링크의 token 쿼리값" }
```

3분이 지났으면 `AU_027`. 성공하면 임시 비밀번호가 메일로 갑니다.

#### `PATCH /api/v1/auth/password` (O)

```json
{ "currentPassword": "임시비밀번호", "newPassword": "NewPassw0rd!", "newPasswordConfirm": "NewPassw0rd!" }
```

성공하면 **모든 세션이 끊기고 쿠키도 삭제**됩니다. 로그인 페이지로 보내 재로그인시키세요.

#### `POST /api/v1/auth/unlock` (X)

```json
{ "email": "user@pairing.com", "role": "FREELANCER", "code": "123456" }
```

`purpose: "UNLOCK"`으로 발송한 인증코드를 확인하고 잠금을 풉니다.

---

## 3. 입력값 규칙 (프론트 검증 기준)

서버도 같은 규칙으로 검증하지만, 프론트에서 먼저 걸러주면 왕복이 줄어듭니다.

| 항목 | 규칙 | 위반 시 에러코드 |
| --- | --- | --- |
| 이메일 | 이메일 형식, 255자 이내. 저장은 소문자로 정규화됨 | `GLOBAL_002` |
| 비밀번호 | 대문자 + 소문자 + 숫자 + 특수문자, 8~20자, 공백 불가 | `AU_010` |
| 비밀번호 확인 | 비밀번호와 일치 | `AU_011` |
| 전화번호 | `01[016789]` 로 시작. 하이픈 있어도 없어도 됨 | `GLOBAL_002` |
| 사업자등록번호 | 하이픈 없이 숫자 10자리 | `GLOBAL_002` |
| 생년월일 | `YYYY-MM-DD`, 만 18세 이상 | `AU_025` |
| 카드번호 | 숫자 11~25자, 하이픈 허용 | `GLOBAL_002` |
| 계좌번호 | 숫자 6~26자, 하이픈 허용 | `GLOBAL_002` |
| 은행 코드 | `/meta/banks` 목록의 code | `AC_006` |
| 카드사 | `/meta/card-companies` 목록의 code (한글명 아님) | 400 |
| 주소 | 객체. `sido`·`roadAddress` 필수, `sigungu` 선택(세종시) | 400 |
| 카드번호 | 숫자 16자리(4자리씩 4묶음). 하이픈·공백 허용 | 400 |
| 계좌번호 | 숫자 10~14자리. 하이픈·공백 허용 | 400 |
| 약관 | 필수 항목 전부 동의 | `TM_002` |

**화면 요구사항으로 프론트가 처리할 것** (서버 관여 없음)

- 전화번호 입력 시 하이픈 자동 삽입
- 비밀번호 눈 아이콘(표시/숨김), 형식 안내 문구
- 이메일 도메인 선택(naver/gmail/daum/nate/직접입력)
- 생년월일은 달력 대신 직접 입력 방식
- 인증 3분 타이머, 재발송 버튼 전환, 연타 방지
- 입력 중 이탈 대비 로컬/세션 스토리지 임시 저장
- 로그인 성공 후 직전 화면 복귀(returnUrl 보관)

---

## 4. 에러코드 대응표

프론트에서 분기가 필요한 것만 모았습니다.

| errorCode | HTTP | 화면 처리 |
| --- | --- | --- |
| `GLOBAL_002` | 400 | `message` 그대로 필드 하단에 표시 |
| `GLOBAL_006` | 401 | 비로그인. 로그인 페이지로 |
| `GLOBAL_009` | 401 | `/auth/refresh` 후 원래 요청 재시도 |
| `GLOBAL_010` | 401 | 토큰 위조. 쿠키 정리 후 재로그인 |
| `GLOBAL_011` | 401 | **다른 기기 로그인 모달** 후 메인/로그인 |
| `GLOBAL_005` | 403 | 권한 없음 안내 |
| `AU_001` | 401 | "ID나 PW가 일치하지 않습니다." |
| `AU_002` | 423 | 잠금 안내 + 이메일 인증(UNLOCK) 화면으로 |
| `AU_003` | 429 | "현재 시각 기준 1시간 뒤에 다시 시도해 주세요" 안내문 |
| `AU_004` | 400 | 코드 불일치. 재입력 |
| `AU_005` | 400 | 코드 만료. 재발송 유도 |
| `AU_012` | 400 | 시도 5회 초과. 재발송부터 |
| `AU_006` | 400 | 이메일 인증 단계로 되돌리기 |
| `AU_007` / `AU_008` / `AU_009` | 409 | 해당 입력칸에 중복 표시 |
| `AU_010` / `AU_011` | 400 | 비밀번호 입력칸에 표시 |
| `AU_013` / `TM_002` | 400 | 필수 약관 체크 유도 |
| `TM_003` | 400 | 약관 목록이 낡음. `GET /terms` 다시 호출 |
| `AU_014` | 429 | IP 차단 안내(2시간). 재시도 막기 |
| `AU_015` | 401 | 다른 기기 로그인 모달 |
| `AU_016` | 401 | 로그인 페이지로 |
| `AU_017` | 403 | 비밀번호 변경 화면으로 |
| `AU_018` | 400 | 소셜 인증 실패. 처음부터 다시 |
| `AU_019` | 409 | 이미 연동된 계정 안내 |
| `AU_020` | 400 | 티켓 만료. 소셜 인증부터 다시 |
| `AU_021` | 403 | 탈퇴 후 30일 재가입 제한 안내 |
| `AU_022` | 403 | 정지 계정 안내 |
| `AU_023` | 400 | 클라이언트는 소셜 불가 (버튼 자체를 감추는 게 우선) |
| `AU_024` | 404 | "일치하는 회원 정보가 없습니다." |
| `AU_025` | 400 | 만 18세 미만 안내 |
| `AU_026` | 500 | 메일 발송 실패. 재시도 유도 |
| `AU_027` | 400 | 링크 만료. 재설정 요청부터 다시 |
| `AU_028` | 400 | 기존과 다른 비밀번호 입력 유도 |
| `GLOBAL_001` | 500 | 일반 오류 안내 + `traceId` 노출(문의용) |

---

## 5. enum 값 모음

프론트에 하드코딩하지 말고 `/api/v1/meta/*`로 받아 쓰는 것을 권장합니다. (아래는 참고용)

| enum | 값 |
| --- | --- |
| `Role` | `CLIENT`, `FREELANCER`, `ADMIN` |
| `VerificationPurpose` | `SIGNUP`, `UNLOCK`, `PROFILE_UPDATE` |
| `PaymentMethodType` | `CARD`, `BANK_ACCOUNT` |
| `EmployeeCount` | `SIZE_1_4`, `SIZE_5_9`, `SIZE_10_49`, `SIZE_50_299`, `SIZE_300_OVER` |
| `TermsCode` | `SERVICE_CLIENT`, `SERVICE_FREELANCER`, `PRIVACY`, `REVIEW_EXPOSURE`, `FEE_NOTICE`, `MARKETING` |
| `BusinessField` | `IT_CONTENTS_AI`, `GAME`, `SALES_DISTRIBUTION_LOGISTICS`, `MANUFACTURING`, `ADVANCED_SCIENCE`, `OTHER_SERVICE`, `FINANCE`, `EDUCATION`, `REAL_ESTATE`, `ARTS_SPORTS_LEISURE`, `HEALTH_WELFARE`, `CONSTRUCTION`, `LODGING_FOOD`, `AGRICULTURE_FISHERY`, `MARKETING`, `WATER_ENVIRONMENT`, `ELECTRICITY_GAS`, `PUBLIC_ADMIN_DEFENSE`, `MINING`, `MEDICAL_HEALTHCARE` |

---

## 6. 자주 막히는 지점

1. **로그인은 되는데 다음 요청이 401** → `credentials: 'include'`(또는 `withCredentials: true`)가 빠졌습니다.
2. **CORS 에러** → 프론트 오리진이 서버 허용 목록에 없습니다. 백엔드에 `CORS_ALLOWED_ORIGINS` 추가 요청.
3. **가입에서 계속 `AU_006`** → 인증 확인 후 30분이 지났거나, 인증한 이메일과 제출한 이메일이 다릅니다(대소문자는 무관).
4. **가입에서 `TM_003`** → 약관 목록을 캐싱해 두고 오래된 `termsId`를 보냈습니다. 가입 화면 진입마다 `GET /terms`를 다시 호출하세요.
5. **소셜 콜백이 두 번 호출됨** → `state`는 1회용입니다. React StrictMode의 이중 실행이나 새로고침을 주의하세요.
6. **`/exists/email`이 400** → `role` 파라미터가 빠졌습니다.
7. **비밀번호 변경 후 401** → 정상입니다. 세션을 일부러 끊으므로 재로그인시키면 됩니다.

---

## 7. 아직 없는 것

이 문서 범위 밖(미구현)입니다. 연동 계획에 참고하세요.

- 마이페이지 조회/수정(결제수단 변경 포함), 회원 탈퇴
- 사업자등록번호 국세청 진위확인 (지금은 형식·중복만 확인)
- 관리자(ADMIN) 계정 생성 경로
- 프로필 이미지 업로드 (파일 API는 `global`에 있으나 계정 도메인에 아직 연결되지 않음)
