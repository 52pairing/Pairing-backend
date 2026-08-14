# [작업 지시서] 백엔드 변경 프론트 연동 (2026-08-14)

## 이 문서를 읽는 AI에게

이 문서는 **`Pairing-frontend` 리포에서 수행할 작업 목록**입니다. 백엔드는 이미 배포 준비가 끝났습니다.

- 이 문서만으로 작업할 수 있게 API 계약을 전부 인라인으로 적었습니다. 다른 문서를 찾지 마세요.
- 파일 경로는 **`Pairing-frontend/` 기준**입니다. 경로와 현재 코드는 2026-08-14 기준으로 실제 확인했습니다.
- **T1~T4 는 반드시 해야 합니다.** 안 하면 해당 화면이 400으로 막힙니다.
- `❓사람 확인` 표시가 있는 항목은 임의로 결정하지 말고 질문하세요.
- 작업 후 `npm run build` 와 `npm test` 가 통과하는지 확인하세요.

---

## 작업 목록

| ID | 작업 | 파일 수 | 필수 |
| --- | --- | --- | --- |
| T1 | 주소를 5칸 객체로 전송 | 7 | ✅ |
| T2 | 카드사 자유입력 → select | 4 | ✅ |
| T3 | 카드·계좌번호 자릿수 검증 | 1 | ✅ |
| T4 | 결제수단 탭 이메일 인증 | 3 | ✅ |
| T5 | 클라이언트 화면에 회사명 표시 | 4 | |
| T6 | 로그인 후 화면 전환 실패 수정 | 4 | |
| T7 | 원격 이미지 호스트 등록 | 1 | |

---

# T1. 주소를 5칸 객체로 전송

## 배경

`address` 가 **문자열 → 객체**로 바뀌었습니다. 한 줄 문자열을 보내면 400입니다.

## API 계약

### 요청 (5칸)

```json
"address": {
  "sido": "서울",
  "sigungu": "강남구",
  "roadAddress": "서울 강남구 테헤란로 152",
  "addressDetail": "10층 1002호",
  "zipCode": "06236"
}
```

| 필드 | 타입 | 필수 | 최대 | 비고 |
| --- | --- | --- | --- | --- |
| `sido` | string | ✅ | 20 | |
| `sigungu` | string | ❌ | 40 | 세종시는 빈 문자열 |
| `roadAddress` | string | ✅ | 255 | **시·도·시·군·구 포함 전체 주소** |
| `addressDetail` | string | ❌ | 255 | 사용자 직접 입력 |
| `zipCode` | string | ❌ | 10 | 숫자 5~6자리 |

### 응답 (두 모양이 함께 옴)

```json
{
  "address": "서울 강남구 테헤란로 152 10층 1002호",
  "addressParts": {
    "sido": "서울", "sigungu": "강남구",
    "roadAddress": "서울 강남구 테헤란로 152",
    "addressDetail": "10층 1002호", "zipCode": "06236"
  }
}
```

- **표시**: `address` (한 줄). 서버가 `roadAddress + addressDetail` 로 합칩니다.
- **수정 폼 초기값**: `addressParts`. 이 변경 이전 가입 계정은 **`null`** 이므로 옵셔널 체이닝 필수.

### 영향 API

| 메서드 | 경로 | 비고 |
| --- | --- | --- |
| POST | `/api/v1/auth/signup/client` | 기존에도 필수 |
| POST | `/api/v1/auth/signup/freelancer` | **신규 필수** |
| POST | `/api/v1/auth/signup/freelancer/social` | **신규 필수** |
| PATCH | `/api/v1/clients/me` | **선택 → 필수** |
| PATCH | `/api/v1/freelancers/me` | **선택 → 필수** |

## 주소 검색 위젯 연동

다음(카카오) 우편번호 위젯을 붙이고 결과를 매핑합니다.

```ts
new daum.Postcode({
  oncomplete: (data) => patch({
    address: {
      sido: data.sido,
      sigungu: data.sigungu,          // 세종시는 "" — 그대로 보냄
      roadAddress: data.roadAddress,  // 자르지 말 것
      addressDetail: "",              // 사용자가 직접 입력
      zipCode: data.zonecode,         // 이름 다름 주의
    },
  }),
}).open();
```

**⚠️ 반드시 지킬 것 3가지**

1. **위젯 응답 객체를 그대로 던지지 말 것.** 5칸으로 골라 담으세요. 서버는 모르는 필드를 조용히 버리고 **200 OK** 를 냅니다. 특히 `zonecode` 를 `zipCode` 로 옮기지 않으면 우편번호만 null 이 된 채 성공합니다.
2. **`data.roadAddress` 를 자르지 말 것.** 시·도·시·군·구가 이미 포함돼 있고, 서버는 이 값을 그대로 화면 표시에 씁니다. 자르면 계약서 갑 주소에서 지역이 사라집니다.
3. **`sido`/`sigungu` 를 직접 이어붙여 표시하지 말 것.** `roadAddress` 에 이미 들어 있어 두 번 나옵니다. 표시는 `address` 한 줄을 쓰세요.

> ❓사람 확인: 위 위젯 필드명(`data.sido` / `data.sigungu` / `data.roadAddress` / `data.zonecode`)은
> 백엔드 담당자가 문서로 확인한 값이 아니라 일반 지식 기반입니다. **위젯을 붙인 뒤 `oncomplete` 의
> `data` 를 한 번 콘솔에 찍어 실제 필드명과 `roadAddress` 값의 형태를 확인하고, 다르면 보고하세요.**

## 파일별 작업

### 1) `src/features/auth/types/signupApiTypes.ts`

`SignupAddress` 타입을 추가하고 세 요청 타입의 `address` 를 교체합니다.

```ts
// 추가
export interface SignupAddress {
  sido: string;
  sigungu: string;
  roadAddress: string;
  addressDetail: string;
  zipCode: string;
}
```

```ts
// ClientSignupRequest — 변경 전
  address: string;
// 변경 후
  address: SignupAddress;
```

`FreelancerSignupRequest` 와 `FreelancerSocialSignupRequest` 에는 **`address: SignupAddress;` 를 신규 추가**합니다 (기존에 없음).

### 2) `src/features/auth/types.ts`

세 폼 타입(`ClientSignupForm`, `FreelancerSignupForm`, `FreelancerSocialSignupForm`)의 주소 상태를 5칸으로 바꿉니다. 현재 `ClientSignupForm.address?: string` 만 있고 프리랜서 쪽에는 없습니다.

```ts
address?: {
  sido: string;
  sigungu: string;
  roadAddress: string;
  addressDetail: string;
  zipCode: string;
};
```

### 3) `src/features/auth/utils/buildSignupRequest.ts`

```ts
// buildClientSignupRequest — 변경 전
  address: (form.address ?? "").trim(),

// 변경 후
  address: {
    sido: form.address?.sido ?? "",
    sigungu: form.address?.sigungu ?? "",
    roadAddress: form.address?.roadAddress ?? "",
    addressDetail: form.address?.addressDetail ?? "",
    zipCode: form.address?.zipCode ?? "",
  },
```

`buildFreelancerSignupRequest` 와 `buildFreelancerSocialSignupRequest` 에도 **같은 블록을 신규 추가**합니다.

### 4) `src/features/auth/components/ClientSignupWizard.tsx`

현재 `<input>` 자유 입력입니다 (175~186행 부근).

```tsx
// 변경 전
<input
  id="company-address"
  value={form.address ?? ""}
  onChange={(event) => patch({ address: event.target.value })}
  placeholder="기업 주소를 입력해 주세요."
/>
```

주소 검색 버튼 + 읽기전용 필드 + 상세주소 입력으로 교체합니다.

- `[주소 찾기]` 버튼 → 위젯 open
- `roadAddress` 는 읽기전용 표시
- `addressDetail` 만 사용자 입력
- 유효성(137행 `(form.address ?? "").trim().length > 0`) → `form.address?.roadAddress` 존재 여부로 변경

### 5) `src/features/auth/components/FreelancerSignupWizard.tsx` — 신규 추가

프리랜서 가입에는 지금 주소 입력이 **아예 없습니다.** 4)와 같은 UI를 단계에 추가하고 유효성 검사에 포함시키세요.

### 6) `src/features/auth/components/FreelancerSocialSignupWizard.tsx` — 신규 추가

5)와 동일합니다.

### 7) `src/features/client/mypage/components/ClientCompanyInfo.tsx`

- 14행 `address: string;` → 5칸 객체 + 한 줄 문자열 둘 다 보관
- 21행 하드코딩 목업 제거
- 111행 표시(`<Info label="회사 주소" value={company.address} />`) → 서버의 한 줄 `address` 사용
- 169~173행 수정 폼 → 주소 찾기 UI, 초기값은 `addressParts` (null 방어 필요)

### 8) 프리랜서 마이페이지 계정정보 수정 — `PATCH /freelancers/me`

`address` 가 **필수**가 됐습니다. 요청 바디에 5칸 객체를 넣으세요. 안 보내면 400입니다.

## 완료 조건

- [ ] 클라이언트 가입 성공 (201)
- [ ] 프리랜서 일반·소셜 가입 성공 (201)
- [ ] 세종시 주소(시·군·구 빈 값)로도 가입 성공
- [ ] 클라이언트·프리랜서 마이페이지 수정 성공 (200)
- [ ] 조회 화면에 주소가 한 줄로 정상 표시 (지역명 중복 없음)
- [ ] `addressParts` 가 `null` 인 옛 계정에서 수정 화면이 깨지지 않음

---

# T2. 카드사 자유입력 → select

## 배경

`cardBrand` 가 자유 문자열 → **enum 코드**로 바뀌었습니다. `"신한카드"` 같은 한글명은 400입니다.

## API 계약

```
GET /api/v1/meta/card-companies        (비로그인 가능)
```

```json
{ "code": "CARD_COMPANIES_FOUND",
  "data": [ { "code": "BC", "label": "BC카드" },
            { "code": "SHINHAN", "label": "신한카드" } ] }
```

전업 8개 + 은행 겸영 17개, 총 25종. `code` 를 `cardBrand` 에 **그대로** 보냅니다.

> **은행 코드와 다릅니다.** 은행(`GET /api/v1/meta/banks`)은 금융결제원 기관코드라 숫자(`"088"`),
> 카드사는 영문 코드(`"SHINHAN"`)입니다. 두 select 를 공통 컴포넌트로 만들 때 값 형식을 섞지 마세요.

### 결제수단 조회 응답에 `cardCompany` 추가

```json
{
  "methodType": "CARD",
  "displayName": "신한카드 **** 1234",
  "cardBrand": "신한카드",      // 화면 표시용 한글명 (기존과 동일)
  "cardCompany": "SHINHAN",     // 신규 — 수정 폼 select 초기값
  "cardLast4": "1234",
  "cardHolder": "홍길동"
}
```

## 파일별 작업

### 1) `src/features/auth/components/CardAccountFields.tsx`

3행 주석 *"카드사(cardBrand)는 별도 meta 엔드포인트가 없어 자유 입력으로 받습니다"* 는 **더 이상 사실이 아닙니다.** 삭제하세요.

48~49행의 자유 입력을 select 로 교체합니다.

```tsx
// 변경 전
<input value={values.cardBrand} onChange={(e) => onChange({ cardBrand: e.target.value })} />

// 변경 후 — GET /api/v1/meta/card-companies 로 채운 select
<select value={values.cardBrand} onChange={(e) => onChange({ cardBrand: e.target.value })}>
  <option value="">카드사를 선택해 주세요.</option>
  {cardCompanies.map((c) => <option key={c.code} value={c.code}>{c.label}</option>)}
</select>
```

82행 `bankCode` 도 자유 입력입니다. `GET /api/v1/meta/banks` 로 select 를 채우세요.
(백엔드가 형식 검사를 추가해서, 숫자가 아닌 값을 보내면 400입니다)

### 2) `src/features/payment/types/payment.ts`

`AccountPaymentMethod` 에 필드를 추가합니다.

```ts
export interface AccountPaymentMethod {
  // ... 기존
  cardBrand: string | null;
  cardCompany: string | null;   // 추가
  // ...
}
```

### 3) `src/features/client/mypage/components/ClientPaymentMethods.tsx`
### 4) `src/features/freelancer/mypage/components/FreelancerPaymentMethods.tsx`

- 카드 수정 폼의 카드사 select **초기값을 `cardCompany`** 로 설정 (한글명 `cardBrand` 로는 항목 선택 불가)
- 표시는 `cardBrand`(한글명) 또는 `displayName` 그대로 유지
- `FreelancerPaymentMethods.tsx` 13행의 하드코딩 폴백(`cardBrand: "신한카드"`)은 서버 값과 무관하므로 그대로 둬도 되지만, 정리하면 좋습니다

## 완료 조건

- [ ] 가입 3종에서 카드사 select 로 선택 후 201
- [ ] 카드 수정 200, 수정 폼 재진입 시 카드사가 선택된 상태로 표시
- [ ] 은행 select 정상 동작

---

# T3. 카드·계좌번호 자릿수 검증

## 규칙

| 항목 | 규칙 | 허용 입력 |
| --- | --- | --- |
| `cardNumber` | 숫자 **16자리** (4자리씩 4묶음) | `1234-5678-1234-5678`, `1234 5678 1234 5678`, `1234567812345678` |
| `accountNo` | 숫자 **10~14자리** | 하이픈·공백 허용. 맨 앞/뒤 하이픈, `11--22` 는 400 |

가입과 마이페이지 수정이 같은 규칙입니다.

## 작업

`src/features/auth/components/CardAccountFields.tsx` 의 유효성 검사(145~148행 부근)를 길이 0 초과에서
자릿수 기준으로 바꾸고, 마이페이지 결제수단 수정 폼에도 같은 검사를 넣습니다.

```ts
const digits = (v: string) => v.replace(/\D/g, "");
const isValidCardNumber = (v: string) => digits(v).length === 16;
const isValidAccountNo = (v: string) => {
  const d = digits(v).length;
  return d >= 10 && d <= 14;
};
```

## 완료 조건

- [ ] 15자리·17자리 카드번호 입력 시 제출 전에 막힘
- [ ] 9자리·15자리 계좌번호 입력 시 제출 전에 막힘
- [ ] 하이픈 포함 정상 입력은 통과

---

# T4. 결제수단 탭 이메일 인증

## 배경

결제수단은 **수정만이 아니라 조회부터** 이메일 인증이 필요해졌습니다. 마스킹해도 은행명·예금주·끝 4자리가
계정을 잠깐 빌린 사람에게 단서가 되기 때문입니다.

## 흐름

```
1. 사용자가 결제수단 탭 클릭
2. POST /api/v1/auth/email-verifications          { email, purpose: "PAYMENT_METHOD" }
3. 사용자가 메일에서 코드 확인
4. POST /api/v1/auth/email-verifications/confirm  { email, purpose: "PAYMENT_METHOD", code }
5. GET  /api/v1/accounts/me/payment-methods       ← 여기서부터 열림
```

- `purpose` 는 반드시 **`PAYMENT_METHOD`**. 기존 `PROFILE_UPDATE` 인증으로는 열리지 않습니다.
- `email` 은 `GET /api/v1/auth/me` 의 `email` 을 사용합니다. **사용자에게 입력받지 마세요.**

## 코드 발송 응답

```json
{ "code": "VERIFICATION_CODE_SENT",
  "data": { "expiresAt": "2026-08-14T09:15:00Z", "remainingSendCount": 14 } }
```

- `expiresAt` — 코드 유효 **3분**. 카운트다운에 사용
- `remainingSendCount` — 1시간 **15회** 발송 제한. 0이면 재발송 버튼 비활성화
- 코드 입력 시도 **5회** 초과 시 `AU_012`. 코드가 폐기되므로 재발송부터 다시

## 인증 실패 응답

```json
{ "status": 400, "errorCode": "AU_006", "message": "이메일 인증을 완료해 주세요." }
```

**아래 세 API 모두** `AU_006` 을 낼 수 있습니다.

| 메서드 | 경로 |
| --- | --- |
| GET | `/api/v1/accounts/me/payment-methods` |
| PUT | `/api/v1/accounts/me/payment-methods/card` |
| PUT | `/api/v1/accounts/me/payment-methods/bank-account` |

## 인증은 탭당 한 번

인증 마커를 **소비하지 않습니다.** 목록 조회 → 카드 수정 → 계좌 수정이 인증 **한 번**으로 끝납니다.
유효 시간 **30분**. 30분 뒤 다시 `AU_006` 이 오면 인증 화면을 다시 띄우세요.

> 프로필 수정(`PROFILE_UPDATE`)은 저장 후 마커를 지우는 1회용이라 동작이 다릅니다. 같은 컴포넌트를
> 재사용한다면 이 차이를 반영하세요.

## 파일별 작업

### 1) `src/features/payment/services/settlementPayment.ts`

`getMyPaymentMethods` / `updateMyCard` / `updateMyBankAccount` 가 `AU_006` 을 던질 수 있습니다.
호출부에서 이 코드를 잡아 인증 화면으로 보내세요. (`ApiException.errorCode === "AU_006"`)

### 2) `src/features/client/mypage/components/ClientPaymentMethods.tsx`
### 3) `src/features/freelancer/mypage/components/FreelancerPaymentMethods.tsx`

- 탭 진입 시 목록 조회를 시도하고, `AU_006` 이면 인증 화면을 먼저 렌더
- 인증 완료 후 목록 조회 재시도
- 기존 이메일 인증 컴포넌트(`src/features/auth/components/EmailOtpField.tsx`)를 재사용하되 `purpose` 를 `PAYMENT_METHOD` 로 전달

> ❓사람 확인: 인증 UI 를 **탭 안 인라인**으로 넣을지 **모달**로 띄울지는 디자인 결정입니다.
> 기존 마이페이지 정보수정 화면의 인증 UX 와 맞추는 것을 권장하지만, 확정 전에 확인하세요.

## 완료 조건

- [ ] 인증 없이 탭 진입 시 인증 화면이 먼저 뜸
- [ ] 인증 후 목록·카드 수정·계좌 수정이 **재인증 없이** 연속 동작
- [ ] 30분 경과 후 재진입 시 인증 화면이 다시 뜸
- [ ] 발송 3분 타이머와 남은 횟수 표시 동작

---

# T5. 클라이언트 화면에 회사명 표시

## 배경

클라이언트는 기업 회원이라 메인·프로필에 담당자 개인 이름이 아니라 **회사명**이 나와야 합니다.

## API 계약

`GET /api/v1/auth/me` 응답에 `companyName` 이 추가됐습니다.

```json
{ "accountId": 3, "email": "owner@pairing.com", "role": "CLIENT",
  "name": "홍길동", "companyName": "주식회사 페어링", "tempPassword": false }
```

- `name` = **담당자명**(대표자 개인 이름)
- `companyName` = **회사명**. 클라이언트만 채워지고 프리랜서는 항상 `null`

## 파일별 작업

### 1) `src/features/auth/types.ts` — `CurrentUserResponse`

```ts
companyName: string | null;   // 추가
```

### 2) `src/features/client/components/ClientMain.tsx` (35~36행)

```tsx
// 변경 전
user?.name ? `${user.name} 님,` : "회원님,"
// 변경 후
user?.companyName ?? user?.name ? `${user.companyName ?? user.name} 님,` : "회원님,"
```

### 3) `src/features/common/components/header/Header.tsx` (25행)

```tsx
// CLIENT 분기만
<ClientHeader name={user?.companyName ?? user?.name} ... />
```

### 4) `src/features/client/mypage/components/ClientProfile.tsx`

```tsx
// 12행 이니셜 — 회사명 기준으로
const initial = (user?.companyName ?? user?.name)?.trim().charAt(0) || "기";

// 35행 큰 글씨 — 회사명
{user?.companyName ?? "기업 회원"}

// 38행 "담당자:" 줄 — name 그대로 유지 (바꾸지 말 것)
담당자: {user?.name ?? "불러오는 중"}
```

프리랜서는 `companyName` 이 항상 `null` 이라 `?? name` 폴백으로 두 역할이 함께 처리됩니다.

---

# T6. 로그인 후 화면 전환 실패 수정

## 배경

**백엔드 변경 없음. 프론트 단독 버그입니다.**

로그인해도 다음 화면으로 안 넘어가고 콘솔에
`Encountered a script tag while rendering React component` 가 함께 뜹니다.
콘솔 에러는 원인이 아니라 **리다이렉트가 걸렸다는 신호**입니다.

## 원인

`getCurrentUser()` 의 모듈 전역 캐시가 **로그인 시점에 비워지지 않습니다.**

```ts
// src/features/auth/services/currentUser.ts
let cachedUser         // 5초 positive cache
let currentUserRequest // in-flight 공유
```

이 둘을 초기화하는 코드가 리포 전체에 없습니다. 여기에 `AuthSessionGuard` 가 `/login` 에서도
`/me` 를 호출해 직전 사용자 정보를 캐시에 채웁니다.

1. 로그인된 상태로 `/login` 진입(뒤로가기 등) → `cachedUser = 이전 사용자`
2. 5초 안에 다른 역할로 로그인 → 쿠키는 새 사용자
3. `RoleGuard` 가 `getCurrentUser()` → **캐시 히트, 이전 사용자 반환**
4. 역할 불일치 → 렌더 중 `redirect()` 와 `AuthSessionGuard` 의 `router.replace()` 가 경쟁
5. 루트부터 클라이언트 리렌더 → 콘솔 에러 + 화면 전환 실패

## 파일별 작업 (우선순위 순)

### 1) `src/features/auth/services/currentUser.ts`

```ts
export const resetCurrentUserCache = () => {
  cachedUser = null;
  cachedAt = 0;
  currentUserRequest = null;
};
```

`login()` 성공 직후와 `logout()` 에서 호출합니다.

### 2) `src/app/login/page.tsx` (75행 부근)

```ts
// 변경 전 — WebSocket 정리를 기다리느라 내비게이션이 지연되고,
//           activate() 예외가 로그인 실패로 오인됩니다
const result = await login({ email, password, role });
await reactivateStomp();
router.push(returnUrl || ROLE_HOME_PATH[role]);

// 변경 후
const result = await login({ email, password, role });
resetCurrentUserCache();
router.push(returnUrl || ROLE_HOME_PATH[role]);
void reactivateStomp();   // 실패해도 로그인에 영향 없게
```

### 3) `src/features/auth/components/AuthSessionGuard.tsx` (32~37행)

`/login`·`/signup` 등 공개 경로에서는 `/me` 를 호출하지 않도록 early return 을 추가합니다.
현재 예외는 비밀번호 재설정 경로 하나뿐이라 로그인 화면에서도 캐시를 채우고 있습니다.

### 4) `src/app/layout.tsx` (49~55행)

```tsx
// 변경 전 — App Router 에서 <head> 안의 <Script> 가 클라이언트 리렌더 시 콘솔 에러를 냄
<head>
  <Script id="pairing-theme-init" strategy="beforeInteractive" ... />
</head>
<body>...</body>

// 변경 후 — <head> 제거하고 <body> 첫 자식으로
<body>
  <Script id="pairing-theme-init" strategy="beforeInteractive" ... />
  ...
</body>
```

### 5) 가드 이중화 정리 (선택)

`AuthSessionGuard`(`router.replace`)와 `RoleGuard`(렌더 중 `redirect`)가 같은 판단을 각각 수행하고
서로 다른 방식으로 이동시킵니다. 한쪽으로 일원화하면 재발을 막을 수 있습니다.

> 1~3 만 고쳐도 증상은 사라집니다. 4는 콘솔 에러 정리, 5는 재발 방지입니다.

## 완료 조건

- [ ] 로그인 → 즉시 역할별 메인으로 이동
- [ ] 로그아웃 후 다른 역할로 재로그인해도 정상 이동
- [ ] 로그인 시 콘솔 에러 없음

---

# T7. 원격 이미지 호스트 등록

## 배경

매칭 후보 카드의 프로필 이미지가 `next/image` 에서 깨집니다.

```
Failed to parse src "dummy/profile/freelancer-0082.png" on `next/image`
```

## 작업

`next.config.ts` 가 현재 비어 있습니다. S3(또는 CloudFront) 호스트를 등록하세요.

```ts
const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "<bucket>.s3.ap-northeast-2.amazonaws.com" },
    ],
  },
};
```

> ⚠️ **백엔드 수정이 아직 적용되지 않았습니다.** matching 도메인의 `CandidateResponse` 가
> `CdnMappable` 을 구현하지 않아 S3 object key 가 날것으로 나가고 있습니다. 백엔드 담당자가
> 한 줄을 적용해야 절대 URL 이 내려옵니다. **둘 다 되어야 이미지가 나옵니다.**
>
> ❓사람 확인: 실제 버킷명·리전, CloudFront 사용 여부는 백엔드/인프라 담당자에게 확인하세요.

---

# 참고 — 프론트 작업이 없는 변경

아래는 **자동 적용되거나 하위 호환**이라 작업이 필요 없습니다. 동작이 달라 보이면 참고하세요.

## 로그인 유지 1시간 + 요청마다 자동 연장

- 액세스 토큰 30분 → **1시간**
- 요청이 들어올 때마다 만료가 미뤄집니다(슬라이딩 세션). 남은 수명이 30분 아래인 요청에서
  서버가 새 `accessToken` 쿠키를 실어 줍니다. **30분 안에 아무 API나 한 번 부르면 세션 유지.**
- HttpOnly 쿠키라 브라우저가 알아서 교체합니다. **프론트 코드 변경 없음.**
- `/auth/refresh` 는 그대로 두세요. 30분 넘게 자리를 비웠다 돌아온 경우에만 쓰입니다.
- 리프레시 7일은 절대 상한이라 계속 활동해도 7일 뒤 재로그인이 필요합니다.

## 필수값 검증 강화 (하위 호환)

원래 보내야 했던 값을 안 보내면 이제 명확한 400이 옵니다.

| 필드 | 기존 | 변경 |
| --- | --- | --- |
| `phone` | 빠뜨리면 **500** | 400 `GLOBAL_002` |
| `businessNo` | 400 `AC_001` | 400 `GLOBAL_002` |
| `bankCode` | 가입·수정 규칙 상이 | 양쪽 동일. 숫자 아니면 400 |

## 프리랜서 프로필 사진 유지

`PATCH /freelancers/me` 에서 `profileFileId` 를 **안 보내면 기존 사진을 유지**합니다.
기존에는 무조건 덮어써서 주소만 고쳐도 사진이 날아갔습니다.
매번 현재 `profileFileId` 를 다시 실어 보내던 코드가 있다면 생략해도 됩니다.

---

# 에러 응답 형식

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

형식은 `"필드명: 메시지"`, 구분자는 `", "` 입니다.

```ts
const fieldErrors = Object.fromEntries(
  message.split(", ")
    .map((part) => part.split(": "))
    .filter((pair) => pair.length === 2)
);
```

> 메시지 본문에 `, ` 가 들어가면 어긋납니다. 파싱 실패 시 `message` 를 통째로 보여주는 폴백을 두세요.

`GLOBAL_002` 가 아닌 코드(`AU_006`, `AC_006` 등)는 `message` 가 단일 문장이라 그대로 띄우면 됩니다.

## 에러 코드

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| `GLOBAL_002` | 400 | 요청 검증 실패 |
| `GLOBAL_006` | 401 | 토큰 없음 |
| `GLOBAL_009` | 401 | Access 토큰 만료 → `/auth/refresh` 후 1회 재시도 |
| `GLOBAL_010` | 401 | 토큰 위조·손상 → 재로그인 |
| `GLOBAL_011` | 401 | 다른 기기 로그인으로 세션 종료 → 모달 후 로그인 페이지 |
| `AU_006` | 400 | 이메일 인증 미완료 (결제수단 탭 진입 포함) |
| `AU_012` | 400 | 인증코드 입력 5회 초과 → 재발송부터 |
| `AC_006` | 400 | 지원하지 않는 은행 코드 |

---

# ⚠️ 배포 순서

**T1~T4 는 백엔드가 구(舊) 요청을 더 이상 받지 않습니다.**

| 화면 | 백엔드만 먼저 배포하면 |
| --- | --- |
| 클라이언트·프리랜서·소셜 회원가입 | 400 |
| 마이페이지 기업정보/계정정보 수정 | 400 |
| 마이페이지 결제수단 탭 | 400 |

**동시 배포가 필요합니다.** 프론트를 먼저 올리는 것도 안 됩니다(신 요청을 구 백엔드가 못 받습니다).
T5~T7 은 순서와 무관합니다.
