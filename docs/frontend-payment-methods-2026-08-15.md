# 결제수단 연동 변경 (2026-08-15)

> 결제수단 관련 변경만 담은 문서입니다. 주소·회원가입·회사명 등 다른 변경은 여기 없습니다.

## 이 문서를 읽는 AI에게

- 대상 저장소는 **`Pairing-frontend`** 입니다. 경로는 모두 그 저장소 기준입니다.
- 백엔드는 **이미 배포 준비가 끝났습니다.** 프론트만 맞추면 됩니다.
- `❓사람 확인` 이 붙은 항목은 임의로 정하지 말고 물어보세요.

---

## 한눈에

| 변경 | 영향 |
| --- | --- |
| 카드사가 **자유 입력 → 선택(enum)** 으로 바뀜 | 💥 안 고치면 카드 수정 400 |
| 카드번호 16자리 / 계좌번호 10~14자리 **검증 추가** | 💥 형식 틀리면 400 |
| 결제수단 **수정 시 이메일 인증** 필요 | 💥 안 고치면 수정 400 |
| 조회 응답에 **`cardCompany`** 필드 추가 | 수정 폼 초기값에 필요 |
| **조회(GET)는 그대로** | ✅ 변경 없음 |

**수수료 결제 모달(`src/features/payment/components/PaymentMethodModal.tsx`)은 고칠 게 없습니다.**
조회만 하기 때문입니다. 파일을 열지 마세요.

---

## 1. API 계약

### 1-1. 조회 — 변경 없음

```
GET /api/v1/accounts/me/payment-methods
```

이메일 인증 **불필요**. 로그인만 되어 있으면 됩니다.

```json
{
  "code": "PAYMENT_METHODS_FOUND",
  "message": "조회에 성공했습니다.",
  "data": [
    {
      "paymentMethodId": 300,
      "methodType": "CARD",
      "displayName": "신한카드 **** 1234",
      "cardBrand": "신한카드",
      "cardCompany": "SHINHAN",
      "cardLast4": "1234",
      "cardHolder": "김개발",
      "bankName": null, "accountLast4": null, "accountHolder": null
    },
    {
      "paymentMethodId": 301,
      "methodType": "BANK_ACCOUNT",
      "displayName": "신한은행 **** 6789",
      "cardBrand": null, "cardCompany": null, "cardLast4": null, "cardHolder": null,
      "bankName": "신한은행",
      "accountLast4": "6789",
      "accountHolder": "김개발"
    }
  ]
}
```

- 목록은 **항상 2건** (카드 1 + 계좌 1). 가입 시 만들어지며 추가·삭제 API 는 없습니다.
- 원본 번호는 **응답에 존재하지 않습니다.** 끝 4자리만 옵니다.
- **신규 필드 `cardCompany`** — 카드사 **코드**(`"SHINHAN"`). 기존 `cardBrand` 는 **한글 표시명**(`"신한카드"`)입니다.
- `cardHolder` 는 기존 계정에서 `null` 일 수 있습니다. 가입 요청에 없던 값이라, 카드를 한 번 수정해야 채워집니다.

> ⚠️ 수정 폼의 카드사 `<select>` 초기값에는 **`cardCompany`** 를 쓰세요.
> `cardBrand`("신한카드")로는 항목이 선택되지 않습니다.

### 1-2. 카드 수정

```
PUT /api/v1/accounts/me/payment-methods/card
```

```json
{ "cardBrand": "SHINHAN", "cardNumber": "1234-5678-9123-4567", "cardHolder": "홍길동" }
```

| 필드 | 규칙 |
| --- | --- |
| `cardBrand` | **필수.** 카드사 **코드**. 요청 필드명은 `cardBrand` 지만 값은 코드입니다 |
| `cardNumber` | **필수.** 숫자 **16자리**. 하이픈·공백 허용 |
| `cardHolder` | **필수.** 50자 이하 |

> ⚠️ 요청 필드명이 `cardBrand` 인데 응답의 `cardBrand` 와 의미가 다릅니다.
> **요청 `cardBrand` = 코드(`"SHINHAN"`), 응답 `cardBrand` = 한글명(`"신한카드"`).**
> 응답값을 그대로 요청에 되돌려 보내면 **400** 입니다. 요청에는 `cardCompany` 값을 넣으세요.

### 1-3. 계좌 수정

```
PUT /api/v1/accounts/me/payment-methods/bank-account
```

```json
{ "bankCode": "088", "accountNo": "110-123-456789", "accountHolder": "홍길동" }
```

| 필드 | 규칙 |
| --- | --- |
| `bankCode` | **필수.** 금융결제원 기관코드 **숫자 3~4자리**. `GET /api/v1/meta/banks` 의 code |
| `accountNo` | **필수.** 숫자 **10~14자리**. 하이픈·공백 허용 |
| `accountHolder` | **필수.** 50자 이하 |

은행은 카드사와 달리 **숫자 코드**입니다. `"SHINHAN"` 같은 문자열을 보내면 400 입니다.

---

## 2. 카드사 목록 API (신규 사용)

```
GET /api/v1/meta/card-companies
```

로그인 불필요(`/api/v1/meta/**` 는 permitAll).

```json
{ "code": "CARD_COMPANIES_FOUND",
  "data": [ { "code": "BC", "label": "BC카드" },
            { "code": "KB", "label": "KB국민카드" },
            { "code": "SHINHAN", "label": "신한카드" } ] }
```

`<option value={code}>{label}</option>` 로 그대로 쓰면 됩니다.

**목록을 하드코딩하지 마세요.** 카드사가 추가되면 배포가 어긋납니다. 현재 25개이며 늘어날 수 있습니다.

---

## 3. 이메일 인증 (수정할 때만)

### 흐름

```
1. 사용자가 "수정" 클릭
2. POST /api/v1/auth/email-verifications          { email, purpose: "PAYMENT_METHOD" }
3. 사용자가 메일에서 6자리 코드 확인
4. POST /api/v1/auth/email-verifications/confirm  { email, purpose: "PAYMENT_METHOD", code }
5. PUT  /api/v1/accounts/me/payment-methods/card  ← 여기서부터 열림
```

- `purpose` 는 반드시 **`PAYMENT_METHOD`**. `PROFILE_UPDATE` 인증으로는 열리지 않습니다.
- `email` 은 `GET /api/v1/auth/me` 의 `email` 을 씁니다. **사용자에게 입력받지 마세요.**

### 발송 응답

```json
{ "code": "VERIFICATION_CODE_SENT",
  "message": "인증코드를 발송했습니다.",
  "data": { "expiresAt": "2026-08-15T09:15:00Z", "remainingSendCount": 14 } }
```

- `expiresAt` — 코드 유효 **3분**. 카운트다운에 사용
- `remainingSendCount` — 1시간 **15회** 발송 제한. 0이면 재발송 버튼 비활성화
- 코드 입력 시도 **5회** 초과 시 `AU_012`. 코드가 폐기되므로 재발송부터 다시

### 확인 응답

```json
{ "code": "VERIFICATION_CONFIRMED", "message": "이메일 인증이 완료되었습니다." }
```

### 인증은 한 번만

인증 마커를 **소비하지 않습니다.** 카드 수정 → 계좌 수정이 인증 **한 번**으로 끝납니다.
유효 시간 **30분**. 30분 뒤 다시 `AU_006` 이 오면 인증 화면을 다시 띄우세요.

> 프로필 수정(`PROFILE_UPDATE`)은 저장 후 마커를 지우는 **1회용**이라 동작이 다릅니다.
> 기존 인증 컴포넌트를 재사용한다면 이 차이를 반영하세요.

---

## 4. 파일별 작업

### 4-1. `src/features/payment/types/payment.ts`

`AccountPaymentMethod` 에 `cardCompany` 를 추가합니다.

```ts
export interface AccountPaymentMethod {
  paymentMethodId: number;
  methodType: "CARD" | "BANK_ACCOUNT";
  displayName: string;
  cardBrand: string | null;     // 한글 표시명 "신한카드"
  cardCompany: string | null;   // ← 신규. 코드 "SHINHAN"
  cardLast4: string | null;
  cardHolder: string | null;
  bankName: string | null;
  accountLast4: string | null;
  accountHolder: string | null;
}
```

### 4-2. `src/features/payment/services/settlementPayment.ts`

`updateMyCard` / `updateMyBankAccount` 가 `AU_006` 을 던질 수 있습니다.
호출부에서 잡아 인증 화면으로 보내세요.

```ts
error instanceof ApiException && error.errorCode === "AU_006"
```

카드사 목록 API 를 부르는 함수를 추가하세요.

```ts
export const getCardCompanies = () =>
  apiCall<{ code: string; label: string }[]>("/api/v1/meta/card-companies");
```

**`getMyPaymentMethods` 는 손대지 마세요.** 인증과 무관합니다.

### 4-3. `src/features/client/mypage/components/ClientPaymentMethods.tsx`
### 4-4. `src/features/freelancer/mypage/components/FreelancerPaymentMethods.tsx`

- 목록 조회는 **지금 그대로** (인증 불필요)
- 카드사 입력을 `<input>` → `<select>` 로 교체. 옵션은 `getCardCompanies()` 결과
- 수정 폼 초기값: `<select>` 에 응답의 **`cardCompany`** 를 바인딩
- 카드번호 16자리 / 계좌번호 10~14자리를 **제출 전에** 검증
- 수정 시 이메일 인증을 요구하고, 완료 후 저장

> ❓사람 확인: 인증을 **"수정" 버튼을 누를 때 선제적으로** 띄울지, **저장 후 `AU_006` 이 왔을 때**
> 띄울지는 UX 결정입니다. 선제적으로 띄우는 쪽이 사용자가 입력을 날리지 않아 낫습니다.

> ❓사람 확인: 인증 UI 를 **인라인**으로 넣을지 **모달**로 띄울지도 디자인 결정입니다.
> 기존 마이페이지 정보수정 화면의 인증 UX 와 맞추는 것을 권장합니다.

### 4-5. `src/features/auth/components/CardAccountFields.tsx` (회원가입 공용)

이 컴포넌트는 가입 화면에서도 씁니다. 3행 주석이 지금 **틀렸습니다.**

```
// 카드사(cardBrand)는 별도 meta 엔드포인트가 없어 자유 입력으로 받습니다
```

`GET /api/v1/meta/card-companies` 가 생겼으므로 이 주석을 지우고 `<select>` 로 바꿔주세요.
가입 요청도 카드사 **코드**를 보내야 합니다.

---

## 5. 에러 코드

| 코드 | status | 의미 | 대응 |
| --- | --- | --- | --- |
| `AU_006` | 400 | 이메일 인증 미완료 | 인증 화면 표시 후 재시도 |
| `AU_012` | 400 | 인증코드 입력 5회 초과 | 재발송부터 다시 |
| `AC_006` | 400 | 지원하지 않는 은행 코드 | 은행 목록에서 다시 선택 |
| `GLOBAL_002` | 400 | 요청 검증 실패 (자릿수·카드사 코드 등) | `message` 표시 |

에러 응답은 성공과 **봉투가 다릅니다.** `data` 로 감싸지 않고 최상위에 옵니다.

```json
{ "timestamp": "2026-08-15T04:12:33.412Z",
  "status": 400,
  "errorCode": "AU_006",
  "message": "이메일 인증을 완료해 주세요.",
  "traceId": "a1b2c3d4" }
```

필드명은 **`errorCode`** 입니다(`code` 아님). `src/lib/api.ts` 가 이미 `ApiException` 으로 변환해 줍니다.

---

## 6. 완료 조건

- [ ] 인증 없이 결제수단 탭에 들어가도 **목록이 그대로 보임**
- [ ] 카드사가 `<select>` 로 뜨고, 목록이 `GET /api/v1/meta/card-companies` 에서 옴
- [ ] 수정 폼을 열면 현재 카드사가 **미리 선택되어 있음** (`cardCompany` 바인딩)
- [ ] 15자리·17자리 카드번호는 제출 전에 막힘
- [ ] 9자리·15자리 계좌번호는 제출 전에 막힘
- [ ] 하이픈 포함 입력(`1234-5678-9123-4567`)은 통과
- [ ] 수정 시 인증 화면이 뜨고, 완료 후 저장됨
- [ ] 인증 후 카드 수정 → 계좌 수정이 **재인증 없이** 연속 동작
- [ ] 30분 뒤 다시 수정하면 인증 화면이 다시 뜸
- [ ] **수수료 결제가 이메일 인증 없이 그대로 동작** (결제 모달 코드 변경 없음)

---

## 7. 배포 순서

**동시 배포가 필요합니다.** 백엔드를 먼저 올리면 카드사 자유 입력이 400 이 되고,
프론트를 먼저 올리면 카드사 코드를 구 백엔드가 못 받습니다.
