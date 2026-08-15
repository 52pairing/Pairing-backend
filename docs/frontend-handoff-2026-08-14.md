# 프론트엔드 연동 안내 (2026-08-14)

백엔드 변경 사항을 한 문서로 모았습니다. **위에서부터 급한 순서**입니다.

| # | 항목 | 프론트 작업 | 성격 |
| --- | --- | --- | --- |
| 1 | [주소 5칸 분리](#1-주소가-5칸으로-나뉩니다) | **필수** | 💥 안 고치면 가입·수정 400 |
| 2 | [카드사 enum + 번호 자릿수](#2-카드사가-enum이-되고-번호-자릿수가-고정됩니다) | **필수** | 💥 안 고치면 가입·수정 400 |
| 3 | [결제수단 탭 이메일 인증](#3-결제수단-탭에-이메일-인증이-생깁니다) | **필수** | 💥 안 고치면 탭이 안 열림 |
| 4 | [프리랜서 가입에 주소 추가](#4-프리랜서-가입에-주소-입력이-생깁니다) | **필수** | 💥 안 고치면 가입 400 |
| 5 | [클라이언트 이름 → 회사명](#5-클라이언트-화면에-회사명을-찍습니다) | 필요 | 화면 표시 |
| 6 | [로그인 후 화면 전환 실패](#6-로그인-후-화면-전환이-가끔-안-되는-문제) | 필요 | 프론트 단독 버그 |
| 7 | [후보 프로필 이미지 깨짐](#7-매칭-후보-프로필-이미지가-깨지는-문제) | 필요 | 백엔드 미적용 |
| 8 | [로그인 유지 1시간 + 자동 연장](#8-로그인이-1시간-유지되고-요청마다-연장됩니다) | 없음 | 자동 적용 |
| 9 | [필수값 검증 강화](#9-필수값-검증이-강해집니다) | 없음 | 하위 호환 |
| 10 | [프리랜서 프로필 사진 유지](#10-프리랜서-프로필-사진이-더-이상-지워지지-않습니다) | 선택 | 하위 호환 |

---

## ⚠️ 배포 순서 — 먼저 읽어주세요

💥 표시한 **1~4번은 백엔드가 구(舊) 요청을 더 이상 받지 않습니다.** 백엔드를 먼저 배포하면
그 순간부터 기존 프론트에서 아래가 전부 실패합니다.

| 화면 | 백엔드만 먼저 배포하면 |
| --- | --- |
| 클라이언트·프리랜서 회원가입 | 400 (주소·카드사) |
| 소셜 가입 추가정보 | 400 (주소·카드사) |
| 마이페이지 기업정보/계정정보 수정 | 400 (주소) |
| 마이페이지 결제수단 탭 | 400 (카드사, 이메일 인증) |

**동시 배포가 필요합니다.** 순차 배포가 불가피하면 프론트를 먼저 올리는 것도 안 됩니다
(신 요청을 구 백엔드가 못 받습니다). 배포 창을 잡아 함께 올리거나, 스테이징에서 프론트·백엔드를
맞춘 뒤 한 번에 반영하는 쪽으로 잡아주세요.

5~10번은 순서와 무관합니다.

---

## 에러 응답 읽는 법

검증 실패(`GLOBAL_002`)는 **필드별 배열이 아니라 `message` 한 줄에 이어붙여** 옵니다.

```json
{
  "timestamp": "2026-08-14T09:12:00Z",
  "status": 400,
  "errorCode": "GLOBAL_002",
  "message": "phone: 전화번호는 필수입니다., businessNo: 사업자등록번호는 필수입니다.",
  "traceId": "a1b2c3d4"
}
```

형식은 `"필드명: 메시지"` 이고 구분자는 `", "` 입니다. 필드별 에러 UI를 만들려면 프론트가 파싱해야 합니다.

```ts
const fieldErrors = Object.fromEntries(
  message.split(", ")
    .map((part) => part.split(": "))
    .filter((pair) => pair.length === 2)
);
```

> 메시지 본문에 `, ` 가 들어가면 어긋날 수 있습니다. 파싱이 실패하면 `message` 를 통째로
> 보여주는 폴백을 두세요.

`errorCode` 가 `GLOBAL_002` 가 아닌 경우(`AU_006`, `AC_006` 등)는 `message` 가 단일 문장이라
그대로 띄우면 됩니다.

---

## 1. 주소가 5칸으로 나뉩니다

`address` 가 **문자열 → 객체**로 바뀝니다. 한 줄 문자열을 보내면 400입니다.

### 보낼 때

```json
"address": {
  "sido": "서울",
  "sigungu": "강남구",
  "roadAddress": "서울 강남구 테헤란로 123",
  "addressDetail": "10층 1002호",
  "zipCode": "06234"
}
```

### 다음(카카오) 우편번호 위젯 → 우리 필드 매핑

**위젯 결과를 가공하지 말고 그대로 옮기면 됩니다.**

| 우리 필드 | 필수 | 위젯 필드 | 값 예시 |
| --- | --- | --- | --- |
| `sido` | ✅ | `data.sido` | `"서울"` |
| `sigungu` | ❌ | `data.sigungu` | `"강남구"` |
| `roadAddress` | ✅ | `data.roadAddress` | `"서울 강남구 테헤란로 152"` |
| `addressDetail` | ❌ | **사용자 직접 입력** | `"10층 1002호"` |
| `zipCode` | ❌ | `data.zonecode` | `"06236"` |

```ts
new daum.Postcode({
  oncomplete: (data) => setAddress({
    sido: data.sido,
    sigungu: data.sigungu,          // 세종시는 "" 로 옵니다. 그대로 보내면 됩니다
    roadAddress: data.roadAddress,  // 자르지 마세요
    addressDetail: "",              // 사용자가 직접 입력
    zipCode: data.zonecode,
  }),
}).open();
```

> **⚠️ `roadAddress` 를 자르지 마세요.** 위젯의 `roadAddress` 에는 시·도·시·군·구가 **이미 포함**돼
> 있습니다(`"서울 강남구 테헤란로 152"`). 앞부분을 잘라 보내려 하면 세종시처럼 시·군·구가 빈 경우에
> 규칙이 지저분해지고, 위젯에는 잘라낸 값을 주는 필드도 없습니다.
> (`data.roadname` 은 `"테헤란로"` 로 건물번호가 빠집니다)
>
> 서버는 한 줄 주소를 만들 때 **`roadAddress` + `addressDetail` 만** 잇습니다.
> `sido`/`sigungu` 를 다시 붙이지 않으므로 중복되지 않습니다.

> **⚠️ 위젯 응답 객체를 그대로 던지지 마세요.** 5칸으로 골라 담아야 합니다. 특히 우편번호는
> 위젯이 `zonecode`, 우리가 `zipCode` 로 **이름이 다릅니다.** 그대로 던지면 서버가 모르는 필드를
> 조용히 버려서 우편번호만 null 이 된 채 **200 OK 가 나갑니다.** (`zipCode` 는 선택 필드라 검증에도
> 안 걸립니다) 실패가 드러나지 않으니 매핑을 꼭 확인하세요.

> **`sido`/`sigungu` 는 지역 구분용입니다.** 화면 표시는 `roadAddress`(또는 한 줄 `address`)가
> 담당합니다. 셋을 직접 이어붙여 표시하면 시·도·시·군·구가 두 번 나옵니다.

> **`sigungu` 가 선택인 이유**: 세종특별자치시는 시·군·구가 없어 위젯이 빈 값을 줍니다.
> 필수로 두면 세종시 사용자가 가입 자체를 못 합니다.

> 서버는 시·군·구 코드표를 들고 있지 않아 "실존하는 지역인가"는 검증하지 않습니다. 형식만 봅니다.

### 받을 때 — 두 가지 모양이 함께 옵니다

```json
{
  "address": "서울 강남구 테헤란로 123 10층 1002호",
  "addressParts": {
    "sido": "서울", "sigungu": "강남구",
    "roadAddress": "서울 강남구 테헤란로 123", "addressDetail": "10층 1002호", "zipCode": "06234"
  }
}
```

- **화면에 찍을 때는 `address`** — 한 줄로 합쳐져 있습니다. 기존 코드를 그대로 두면 됩니다.
- **수정 폼 입력칸을 채울 때만 `addressParts`**

> 이 변경 이전에 가입한 계정은 `addressParts` 가 `null` 입니다. 그때도 `address` 는 옛 값이
> 그대로 나가므로 **조회 화면은 정상**입니다. 수정 폼은 주소 찾기를 다시 시키면 되고,
> 한 번 저장하면 `addressParts` 가 채워집니다. `addressParts?.sido ?? ""` 식의 방어가 필요합니다.

### 영향 받는 API

| API | 비고 |
| --- | --- |
| `POST /auth/signup/client` | 필수 |
| `POST /auth/signup/freelancer` | 필수 (신규 — 4번 항목) |
| `POST /auth/signup/freelancer/social` | 필수 (신규 — 4번 항목) |
| `PATCH /clients/me` | **필수로 바뀜** (기존에는 생략 가능) |
| `PATCH /freelancers/me` | **필수로 바뀜** |

> 마이페이지 수정에서 주소가 필수가 된 이유: 가입 때 필수인 값이고, 클라이언트 주소는
> 계약서 갑(甲) 주소로 쓰여서 비면 계약서가 깨집니다.

**이력서(`PUT /freelancers/me/resume`)는 이번 변경 대상이 아닙니다.** 기존 3칸
(`zipCode` / `address` / `addressDetail`) 구조 그대로입니다. 헷갈리지 않도록 주의해 주세요.

---

## 2. 카드사가 enum이 되고 번호 자릿수가 고정됩니다

### 카드사 — 목록 API에서 코드를 받아 그대로 전송

```
GET /api/v1/meta/card-companies      (비로그인 가능)
```

```json
{ "code": "CARD_COMPANIES_FOUND",
  "data": [ { "code": "BC", "label": "BC카드" }, { "code": "SHINHAN", "label": "신한카드" }, ... ] }
```

`cardBrand` 에 **`code` 를 그대로** 보냅니다. `"신한카드"` 같은 한글명은 400입니다.

> **은행과 다릅니다.** 은행은 금융결제원 기관코드라 숫자(`"088"`)지만, 카드사는 그런 외부 규격이
> 없어서 영문 코드(`"SHINHAN"`)입니다. 두 select 를 같은 컴포넌트로 만들 때 주의하세요.

전업 8개(BC·KB국민·하나·삼성·신한·현대·롯데·우리) + 은행 겸영 17개, 총 25종입니다.

### 번호 자릿수

| 항목 | 규칙 | 허용 입력 |
| --- | --- | --- |
| `cardNumber` | 숫자 **16자리** (4자리씩 4묶음) | `1234-5678-1234-5678`, `1234 5678 1234 5678`, `1234567812345678` |
| `accountNo` | 숫자 **10~14자리** | 하이픈·공백 허용. 맨 앞/뒤 하이픈이나 `11--22` 는 400 |

가입과 마이페이지 수정이 **같은 규칙**을 씁니다. (기존에는 두 곳 정규식이 달라, 가입은 통과한
카드번호가 수정에서 막히는 조합이 있었습니다)

### 조회 응답에 `cardCompany` 가 추가됩니다

```json
{
  "methodType": "CARD",
  "displayName": "신한카드 **** 1234",
  "cardBrand": "신한카드",      // 화면 표시용 한글명 — 기존과 동일
  "cardCompany": "SHINHAN",     // 신규. 수정 폼 select 초기값
  "cardLast4": "1234",
  "cardHolder": "홍길동"
}
```

**수정 폼의 카드사 select 초기값은 `cardCompany` 를 쓰세요.** 한글명으로는 항목을 고를 수 없습니다.

### 영향 받는 API

`POST /auth/signup/{client,freelancer,freelancer/social}`, `PUT /accounts/me/payment-methods/card`

---

## 3. 결제수단 탭에 이메일 인증이 생깁니다

**수정만이 아니라 조회부터** 인증이 필요합니다. 마스킹해서 내려도 은행명·예금주·끝 4자리가
계정을 잠깐 빌린 사람에게 단서가 되기 때문입니다.

### 흐름

```
1. 사용자가 결제수단 탭 클릭
2. POST /api/v1/auth/email-verifications          { email, purpose: "PAYMENT_METHOD" }
3. 사용자가 메일에서 코드 확인
4. POST /api/v1/auth/email-verifications/confirm  { email, purpose: "PAYMENT_METHOD", code }
5. GET  /api/v1/accounts/me/payment-methods       ← 여기서부터 열림
```

`purpose` 는 반드시 **`PAYMENT_METHOD`** 입니다. 기존 `PROFILE_UPDATE` 인증으로는 열리지 않습니다.
사용자가 프로필 화면에서 받은 코드로 결제수단까지 열리면 안 되기 때문입니다.

`email` 은 `GET /auth/me` 의 `email` 을 그대로 씁니다. 사용자에게 입력받지 마세요.

### 코드 발송 응답

```json
{ "code": "VERIFICATION_CODE_SENT",
  "data": { "expiresAt": "2026-08-14T09:15:00Z", "remainingSendCount": 14 } }
```

- `expiresAt` — **코드 유효 3분.** 이 값으로 카운트다운을 그리세요.
- `remainingSendCount` — **1시간에 15회** 발송 제한. 남은 횟수입니다. 0이면 재발송 버튼을 막으세요.
- 코드 **입력 시도는 5회**입니다. 초과하면 `AU_012` 가 오고 코드가 폐기되므로 재발송부터 다시 해야 합니다.

### 인증 없이 부르면

```json
{ "status": 400, "errorCode": "AU_006", "message": "이메일 인증을 완료해 주세요." }
```

**세 API 모두 `AU_006` 을 낼 수 있습니다.**

| API | 기존 | 변경 |
| --- | --- | --- |
| `GET /accounts/me/payment-methods` | 인증 불필요 | **AU_006 가능** |
| `PUT /accounts/me/payment-methods/card` | 인증 불필요 | **AU_006 가능** |
| `PUT /accounts/me/payment-methods/bank-account` | 인증 불필요 | **AU_006 가능** |

### 인증은 한 번만 받으면 됩니다

인증 마커를 **소비하지 않습니다.** 탭에 들어가 목록을 보고 → 카드를 고치고 → 계좌까지 고치는
흐름이 인증 **한 번**으로 끝납니다.

유효 시간은 **30분**(`app.auth.verified-marker-ttl`)입니다. 30분이 지나면 다시 `AU_006` 이 나오므로,
그때 인증 화면을 다시 띄우면 됩니다.

> 프로필 수정(`PROFILE_UPDATE`)은 저장 후 마커를 지우는 1회용이라 동작이 다릅니다. 혼동 주의.

### 다른 화면은 영향 없습니다

계약서의 정산 계좌 표기, 정산 목록의 `"신한카드 **** 1234"` 표기는 **인증 없이 그대로** 동작합니다.
관문은 마이페이지 결제수단 화면에만 걸었습니다.

---

## 4. 프리랜서 가입에 주소 입력이 생깁니다

프리랜서 가입은 지금까지 주소를 안 받았고, 가입 후 마이페이지에서만 채울 수 있었습니다.
이제 **가입 시점에 필수**입니다.

- `POST /auth/signup/freelancer` — `address` 필수
- `POST /auth/signup/freelancer/social` — `address` 필수

형식은 1번 항목과 동일합니다. **소셜 가입 추가정보 화면에도 주소 입력이 들어가야 합니다.**

---

## 5. 클라이언트 화면에 회사명을 찍습니다

클라이언트는 기업 회원이라 메인·프로필에 담당자 개인 이름이 아니라 **회사명**이 나와야 합니다.

`GET /api/v1/auth/me` 응답에 `companyName` 이 추가됐습니다.

```json
{ "accountId": 3, "email": "owner@pairing.com", "role": "CLIENT",
  "name": "홍길동", "companyName": "주식회사 페어링", "tempPassword": false }
```

- `name` = **담당자명**(대표자 개인 이름). 클라이언트도 회사명이 아닙니다.
- `companyName` = **회사명**. 클라이언트만 채워지고 프리랜서는 항상 `null`

### 왜 `name` 을 덮어쓰지 않았나

프로필 화면이 회사명과 담당자명을 **동시에** 보여줍니다. `name` 에 회사명을 넣으면
"담당자: 주식회사 페어링" 이 됩니다.

### 프론트 수정

```ts
// types.ts — CurrentUserResponse
companyName: string | null;
```

| 위치 | 변경 |
| --- | --- |
| 메인 히어로 인사말 | `user?.companyName ?? user?.name` |
| 헤더(CLIENT 분기) | `user?.companyName ?? user?.name` |
| 마이페이지 프로필 큰 글씨 | `user?.companyName ?? "기업 회원"` |
| 마이페이지 "담당자:" 줄 | `user?.name` **그대로 유지** |
| 프로필 이니셜 원 | 회사명 기준으로 변경 |

프리랜서는 `companyName` 이 항상 `null` 이라 `?? name` 폴백으로 두 역할이 함께 처리됩니다.

---

## 6. 로그인 후 화면 전환이 가끔 안 되는 문제

**백엔드 변경 없음. 프론트 단독 이슈입니다.**

로그인해도 다음 화면으로 안 넘어가고, 콘솔에
`Encountered a script tag while rendering React component` 가 함께 뜨는 증상입니다.
콘솔 에러는 원인이 아니라 **리다이렉트가 걸렸다는 신호**입니다.

### 원인

`getCurrentUser()` 의 모듈 전역 캐시가 **로그인 시점에 비워지지 않습니다.**

```ts
let cachedUser        // 5초 positive cache
let currentUserRequest // in-flight 공유
```

이 둘을 초기화하는 코드가 리포 전체에 없습니다. 여기에 `AuthSessionGuard` 가 `/login` 에서도
`/me` 를 호출해 **직전 사용자 정보를 캐시에 채웁니다.**

1. 로그인된 상태로 `/login` 진입(뒤로가기 등) → `cachedUser = 이전 사용자`
2. 5초 안에 다른 역할로 로그인 → 쿠키는 새 사용자
3. `RoleGuard` 가 `getCurrentUser()` → **캐시 히트, 이전 사용자 반환**
4. 역할 불일치 → 렌더 중 `redirect()` + `AuthSessionGuard` 의 `router.replace()` 가 경쟁
5. 루트부터 클라이언트 리렌더 → 콘솔 에러 + 화면 전환 실패

### 판별

로그인 클릭 시 Network 탭의 `/api/v1/auth/me`:

| 관찰 | 원인 |
| --- | --- |
| 요청 자체가 없음 | 캐시 히트 (확정) |
| 200인데 role 이 방금 로그인한 역할과 다름 | 캐시 히트 (확정) |
| 401 `GLOBAL_006` | in-flight promise 공유 |
| 401 `GLOBAL_011` | 재로그인 경합 (아래 참고) |

### 수정

| 순위 | 파일 | 내용 |
| --- | --- | --- |
| 1 | `features/auth/services/currentUser.ts` | `resetCurrentUserCache()` 를 export 하고 `login()` 성공 직후·`logout()` 에서 호출 |
| 2 | `app/login/page.tsx` | `await reactivateStomp()` 를 `router.push` **뒤로** 옮기거나 `void` 처리. 현재는 WebSocket 정리를 기다리느라 내비게이션이 지연되고, `activate()` 예외가 로그인 실패로 오인됩니다 |
| 3 | `AuthSessionGuard.tsx` | `/login`·`/signup` 등 공개 경로에서는 `/me` 호출하지 않기 |
| 4 | `app/layout.tsx` | `<head>` 제거하고 `<Script beforeInteractive>` 를 `<body>` 첫 자식으로. 콘솔 에러가 사라집니다 |
| 5 | 가드 이중화 | `AuthSessionGuard`(router.replace)와 `RoleGuard`(렌더 중 redirect)가 같은 판단을 각각 수행합니다. 한쪽으로 일원화 권장 |

1~3만 고쳐도 증상은 없어집니다.

---

## 7. 매칭 후보 프로필 이미지가 깨지는 문제

> ⚠️ **백엔드 수정이 아직 적용되지 않았습니다.** 아래 두 가지가 **모두** 들어가야 이미지가 나옵니다.

### 증상

```
Failed to parse src "dummy/profile/freelancer-0082.png" on `next/image`
```

### 백엔드 (미적용)

`CandidateResponse` 가 `CdnMappable` 을 구현하지 않아 S3 object key 가 날것으로 나갑니다.
다른 도메인 응답 DTO(`FreelancerMyPageResponse`, `ResumeResponse`, `ChatMessageResponse` 등)는
전부 붙어 있는데 matching 도메인만 누락됐습니다. 한 줄 수정이면 됩니다.

적용되면 이렇게 나갑니다.

```
https://{bucket}.s3.ap-northeast-2.amazonaws.com/dummy/profile/freelancer-0082.png
```

### 프론트

`next.config.ts` 가 비어 있어 원격 호스트가 차단됩니다. `images.remotePatterns` 에
S3(또는 CloudFront) 호스트를 등록해야 합니다. **백엔드만 고치면 에러 메시지만 바뀌고
이미지는 여전히 안 나옵니다.**

---

## 8. 로그인이 1시간 유지되고 요청마다 연장됩니다

**프론트 수정 없음.** HttpOnly 쿠키라 브라우저가 알아서 교체합니다.

- 액세스 토큰 수명 30분 → **1시간**
- **요청이 들어올 때마다 만료가 미뤄집니다**(슬라이딩 세션). 남은 수명이 30분 아래인 요청에서
  서버가 새 `accessToken` 쿠키를 응답에 실어 줍니다.
- 즉 **30분 안에 아무 API나 한 번이라도 부르면 세션이 끊기지 않습니다.**
- 리프레시 토큰 7일은 **절대 상한**입니다. 계속 활동해도 7일 뒤에는 재로그인이 필요합니다.

### `/auth/refresh` 는 그대로 두세요

평소에는 부를 일이 거의 없어집니다. 필요한 경우는 **30분 넘게 자리를 비웠다 돌아온 상황**뿐이고,
그때는 기존대로 `GLOBAL_009` → `/auth/refresh` → 원래 요청 1회 재시도 흐름이 동작합니다.

---

## 9. 필수값 검증이 강해집니다

**하위 호환입니다.** 원래 보내야 했던 값을 안 보내면 이제 명확한 400이 온다는 것뿐입니다.

| 필드 | 기존 | 변경 |
| --- | --- | --- |
| `phone` (가입 3종 + 마이페이지 수정 2종) | 빠뜨리면 **500** | 400 `GLOBAL_002` |
| `businessNo` (클라이언트 가입) | 400 `AC_001` (어느 칸인지 모름) | 400 `GLOBAL_002` |
| `bankCode` | 가입·수정 규칙이 달랐음 | 양쪽 동일 |

`bankCode` 에 형식 검사가 생기면서, 카드사 코드를 실수로 보낸 경우(`"SHINHAN"`)가
`AC_006` 이 아니라 **400 형식 오류**로 바뀝니다. 숫자지만 목록에 없는 코드(`"999"`)는 그대로 `AC_006` 입니다.

---

## 10. 프리랜서 프로필 사진이 더 이상 지워지지 않습니다

`PATCH /freelancers/me` 에서 `profileFileId` 를 **안 보내면 기존 사진을 그대로 둡니다.**

기존에는 무조건 덮어써서, 사진을 안 건드리고 주소만 고쳐 저장하면 사진이 날아갔습니다.
기업 로고(`logoFileId`)와 같은 규칙이 됐습니다.

프론트에서 매번 현재 `profileFileId` 를 다시 실어 보내던 코드가 있다면 이제 생략해도 됩니다.

---

## 부록 — 참고할 기존 문서

| 문서 | 내용 |
| --- | --- |
| `docs/frontend-integration.md` | 가입·로그인·메타 API 전반 |
| `docs/frontend-auth-integration.md` | 인증·세션·쿠키 상세 |
| `docs/frontend-mypage-integration.md` | 마이페이지 전 화면 |
| `docs/frontend-session-cookie-expiry.md` | 세션 만료 401 처리 |
| `.ai/API.md` | 전체 엔드포인트 목록 |
| `docs/api-dto.csv` | 필드 단위 스펙 |

## 부록 — 에러 코드

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| `GLOBAL_002` | 400 | 요청 검증 실패. 어느 칸이 틀렸는지 함께 옵니다 |
| `GLOBAL_006` | 401 | 토큰 없음 |
| `GLOBAL_009` | 401 | Access 토큰 만료 → `/auth/refresh` 후 1회 재시도 |
| `GLOBAL_010` | 401 | 토큰 위조·손상 → 재로그인 |
| `GLOBAL_011` | 401 | 다른 기기 로그인으로 세션 종료 → 모달 후 로그인 페이지 |
| `AU_006` | 400 | **이메일 인증 미완료** (결제수단 탭 진입 포함) |
| `AU_012` | 400 | 인증코드 입력 5회 초과. 코드가 폐기되므로 재발송부터 |
| `AC_006` | 400 | 지원하지 않는 은행 코드 |
