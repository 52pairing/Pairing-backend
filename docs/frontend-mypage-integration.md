# 프론트엔드 연동 가이드 — 마이페이지 · 등급 · 리뷰 · 알림 · 고객지원 · 파일

프리랜서/클라이언트 **마이페이지**(기본정보 · 결제수단), **등급·혜택**, **리뷰·평점**, **알림**,
**고객지원(챗봇·1:1 문의)**, **파일 업로드**, 비로그인 메인의 **사이트 후기** 연동 문서입니다.

계정·인증 공통 규약은 [frontend-auth-integration.md](frontend-auth-integration.md) 를,
프로젝트는 [frontend-project-integration.md](frontend-project-integration.md) 를 참고하세요.

- 이 문서에 없는 화면(추천 후보 · 협상 · 계약 · 정산)은 **매칭 / 협상 / 계약 / 정산 도메인** 담당입니다.
- 회원 탈퇴 · 관리자 회원관리는 **계정(Account) 도메인** 담당이라 이 문서에 없습니다.
  (마이페이지 **결제수단** 탭은 경로가 `/api/v1/accounts/**` 지만 이 문서 3-3 에 있습니다)

---

## 1. 공통 규약

### 응답 봉투

성공

```json
{
  "timestamp": "2026-08-10T07:12:41.412301200Z",
  "status": 200,
  "code": "MY_PAGE_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { }
}
```

실패

```json
{
  "timestamp": "2026-08-10T06:45:15.734556900Z",
  "status": 400,
  "errorCode": "FR_003",
  "message": "이력서 정보가 올바르지 않습니다.",
  "traceId": "f3b8d1d7"
}
```

성공은 `code`, 실패는 `errorCode` 입니다. **필드 이름이 다릅니다.**

### 페이지 응답

목록 API 의 `data` 는 이 형태입니다.

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 12,
  "totalPages": 2,
  "first": true,
  "last": false
}
```

### 단위·형식 규칙

| 항목 | 규칙 |
|---|---|
| 날짜 | `2026-03-01` (ISO date) |
| 일시 | `2026-08-10T07:12:41` (ISO date-time) |
| 전화번호 | 요청은 하이픈 있어도 되고(`010-1234-5678`), **응답은 항상 숫자만**(`01012345678`) |
| 금액 | 원 단위 정수 (`5000000`) |
| 별점 | 1~5 정수 |
| 파일 | 요청 본문에는 **`fileId`(숫자)만** 넣고, 응답으로는 `~Url` 절대경로를 받습니다 |

### 인증

로그인 시 `accessToken` 이 **HttpOnly 쿠키**로 내려갑니다. 별도 헤더 세팅 없이
`credentials: 'include'` 만 켜 주세요.

권한 관련 응답:

| 상황 | status | errorCode |
|---|---|---|
| 미로그인 | 401 | `GLOBAL_006` |
| 토큰 만료 | 401 | `GLOBAL_009` |
| 다른 기기에서 로그인됨 | 401 | `GLOBAL_011` |
| 역할 불일치 (예: 클라이언트가 프리랜서 API 호출) | 403 | `GLOBAL_005` |

> `/api/v1/freelancers/**` 는 `ROLE_FREELANCER` 전용, `/api/v1/clients/**` 는 `ROLE_CLIENT` 전용입니다.
> 화면 진입 시점에 역할을 확인해 주세요.

---

## 2. 프리랜서 마이페이지

### 2-1. 기본 정보 조회

`GET /api/v1/freelancers/me` · `ROLE_FREELANCER`

```json
{
  "accountId": 7,
  "name": "홍길동",
  "email": "user@pairing.com",
  "phone": "01012345678",
  "birthDate": "1995-03-01",
  "address": "서울 강남구",
  "profileImageUrl": "https://cdn.../profile_image/uuid.png",
  "aiMatchingAgreed": true,
  "grade": "SENIOR",
  "ratingAverage": 4.5,
  "reviewCount": 12,
  "resumeCompleted": true,
  "withdrawable": true
}
```

- `name` · `email` · `birthDate` 는 **수정 불가**(화면에서 readonly 처리)
- `profileImageUrl` 은 미등록이면 `null` → 이니셜 아바타로 대체해 주세요
- `grade`: `JUNIOR` | `SENIOR` | `MASTER`
- `ratingAverage` 는 리뷰가 없으면 `null`
- ⚠️ `withdrawable` 은 **현재 항상 `true`** 입니다 (정산 도메인 연동 대기 중)

### 2-2. 기본 정보 수정 — 이메일 인증 선행 필요 ⚠️

`PATCH /api/v1/freelancers/me` · `ROLE_FREELANCER`

**중요: 비밀번호 확인이 아니라 이메일 인증 방식입니다.** 수정 전에 인증을 먼저 마쳐야 합니다.

```
1) POST /api/v1/auth/email-verifications          { "email": "...", "purpose": "PROFILE_UPDATE" }
2) POST /api/v1/auth/email-verifications/confirm  { "email": "...", "purpose": "PROFILE_UPDATE", "code": "123456" }
3) PATCH /api/v1/freelancers/me
```

요청

```json
{
  "profileFileId": 3,
  "phone": "010-1234-5678",
  "address": "서울 강남구",
  "aiMatchingAgreed": true
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `profileFileId` | | 프로필 사진. `POST /api/v1/files` 로 먼저 업로드 |
| `phone` | | `^01[016789]-?\d{3,4}-?\d{4}$` |
| `address` | | 255자 이하 |
| `aiMatchingAgreed` | ✅ | 필수 |

응답은 2-1 과 동일한 구조입니다.

에러

| status | errorCode | 상황 |
|---|---|---|
| 400 | `AU_006` | **이메일 인증을 안 마쳤음** |
| 404 | `AC_002` | 프로필 없음 |

> **인증 마커는 1회용입니다.** 한 번 수정하면 소모되므로, 연속 수정 시 매번 다시 인증해야 합니다.
> 인증 유효시간은 30분, 인증코드 자체는 3분입니다.

### 2-3. 희망 조건 조회

`GET /api/v1/freelancers/me/condition`

미등록이면 `data` 가 `null` 입니다. (404 가 아닙니다)

```json
{
  "conditionId": 50,
  "jobCategory": "DEVELOPMENT",
  "jobRole": "BACKEND",
  "affiliation": "프리랜서",
  "workStyle": "REMOTE",
  "workForm": "FULL_TIME",
  "payUnit": "MONTHLY",
  "payAmount": 5000000,
  "minAcceptAmount": 4000000,
  "availableFrom": "2026-09-01",
  "startNegotiable": true,
  "periodValue": 6,
  "periodUnit": "MONTH",
  "hasFreelanceExperience": true,
  "careerYears": 5,
  "skills": [{ "skillCode": "JAVA", "skillLevel": "ADVANCED" }]
}
```

### 2-4. 희망 조건 등록/수정

`PUT /api/v1/freelancers/me/condition` — 없으면 생성, 있으면 **전체 교체**

```json
{
  "jobCategory": "DEVELOPMENT",
  "jobRole": "BACKEND",
  "affiliation": "프리랜서",
  "workStyle": "REMOTE",
  "workForm": "FULL_TIME",
  "payUnit": "MONTHLY",
  "payAmount": 5000000,
  "minAcceptAmount": 4000000,
  "availableFrom": "2026-09-01",
  "startNegotiable": true,
  "periodValue": 6,
  "periodUnit": "MONTH",
  "hasFreelanceExperience": true,
  "careerYears": 5,
  "skills": [{ "skillCode": "JAVA", "skillLevel": "ADVANCED" }]
}
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `jobCategory`, `jobRole`, `workStyle`, `workForm`, `payUnit`, `periodUnit` | ✅ | enum |
| `payAmount` | ✅ | 최소 10,000 |
| `minAcceptAmount` | | 최소 10,000. 협상 하한선 힌트 |
| `periodValue` | | 1~24. **협의 가능이면 비워서 보냄**(null 허용) |
| `hasFreelanceExperience` | ✅ | |
| `careerYears` | ✅ | 1~50 |
| `skills` | ✅ | 1개 이상 |
| `affiliation` | | 100자 이하 |

### 2-5. 이력서 조회 (조건 포함)

`GET /api/v1/freelancers/me/resume`

"내 이력서" 화면을 한 번에 그릴 수 있게 조건과 이력서를 **함께** 내려줍니다.

```json
{
  "status": "COMPLETED",
  "lastModifiedAt": "2026-08-10T07:12:41",
  "condition": { },
  "resume": { },
  "notice": "수정한 이력서는 새로운 추천부터 반영됩니다. ..."
}
```

- `status`: `DRAFT` | `COMPLETED` — 미등록이면 `DRAFT`
- `condition` / `resume` 은 미등록이면 각각 `null`
- `notice` 는 서버가 주는 안내 문구 → 그대로 노출해 주세요

`resume` 객체

```json
{
  "resumeId": 60,
  "status": "COMPLETED",
  "name": "홍길동",
  "birthDate": "1995-03-01",
  "contactPhone": "01012345678",
  "contactEmail": "user@pairing.com",
  "address": "서울 강남구",
  "profileImageUrl": "https://cdn.../profile_image/uuid.png",
  "selfIntroduction": "백엔드 5년차입니다.",
  "portfolioUrl": "https://cdn.../portfolio/uuid.pdf",
  "educations": [{
    "startDate": "2014-03-01", "endDate": "2018-02-28",
    "schoolName": "페어링대학교", "major": "컴퓨터공학",
    "graduationStatus": "GRADUATED", "campusType": "MAIN"
  }],
  "careers": [{
    "startDate": "2018-03-01", "endDate": "2023-02-28",
    "companyName": "주식회사 예시", "departmentRank": "서버개발팀 대리",
    "jobDescription": "결제 시스템 개발"
  }],
  "certificates": [{
    "acquiredDate": "2020-05-01", "name": "정보처리기사",
    "issuerScore": "한국산업인력공단", "note": null
  }],
  "links": ["https://github.com/pairing"],
  "agreements": {
    "profileCollectionAgreed": true, "profileProvisionAgreed": true,
    "aiAnalysisAgreed": true, "careerPortfolioUsageAgreed": true
  }
}
```

- 마지막 수정 시각은 `resume` 안이 아니라 **상위의 `lastModifiedAt`** 에 있습니다
- `name` · `birthDate` 는 계정에서 가져오며 **수정 불가**
- `contactPhone` / `contactEmail` 은 요청에서 비우면 **계정 값으로 채워져서** 내려옵니다
- `links` 는 응답에서 **문자열 배열**입니다 (요청은 객체 배열 — 아래 참고)

### 2-6. 이력서 등록/수정

`PUT /api/v1/freelancers/me/resume` — 없으면 생성, 있으면 **전체 교체**

```json
{
  "profileFileId": 3,
  "contactPhone": "010-1234-5678",
  "contactEmail": "user@pairing.com",
  "address": "서울 강남구",
  "selfIntroduction": "백엔드 5년차입니다.",
  "portfolioFileId": 4,
  "educations": [{
    "startDate": "2014-03-01", "endDate": "2018-02-28",
    "schoolName": "페어링대학교", "major": "컴퓨터공학",
    "graduationStatus": "GRADUATED", "campusType": "MAIN"
  }],
  "careers": [{
    "startDate": "2018-03-01", "endDate": "2023-02-28",
    "companyName": "주식회사 예시", "departmentRank": "서버개발팀 대리",
    "jobDescription": "결제 시스템 개발"
  }],
  "certificates": [{
    "acquiredDate": "2020-05-01", "name": "정보처리기사",
    "issuerScore": "한국산업인력공단", "note": null
  }],
  "links": [{ "url": "https://github.com/pairing" }],
  "agreements": {
    "profileCollectionAgreed": true, "profileProvisionAgreed": true,
    "aiAnalysisAgreed": true, "careerPortfolioUsageAgreed": true
  }
}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `profileFileId` | ✅ | 이미지 (jpg/jpeg/png, 5MB) |
| `portfolioFileId` | ✅ | **PDF 만** (100MB) |
| `educations` | ✅ | 1건 이상 |
| `careers` | ✅ | 1건 이상 |
| `certificates` | | 선택 |
| `links` | | 선택. **요청은 `[{ "url": "..." }]` 객체 배열** |
| `agreements` | ✅ | 4개 모두 `true` 여야 통과 |
| `contactPhone`, `contactEmail` | | 비우면 계정 값 사용 |

- 필수 항목을 모두 채우면 `status` 가 `COMPLETED` 가 되고 **매칭 대상에 포함**됩니다
- `agreements` 는 최초 등록 시에만 유효하고, 이후 수정에는 영향을 주지 않습니다

에러

| status | errorCode | 상황 |
|---|---|---|
| 400 | `FR_003` | 이력서 필드 오류 |
| 400 | `FR_004` | 필수 약관 미동의 |
| 400 | `GLOBAL_002` | 학력/경력 0건 등 검증 실패 |

### 2-7. 매칭 설정

`GET /api/v1/freelancers/me/matching-settings`
`PUT /api/v1/freelancers/me/matching-settings`

요청

```json
{ "aiMatchingAgreed": true, "matchingPaused": false }
```

응답

```json
{
  "aiMatchingAgreed": true,
  "matchingPaused": false,
  "matchable": false,
  "unmatchableReason": "이력서를 완성해야 추천 대상에 포함됩니다."
}
```

`matchable` 이 `false` 일 때 `unmatchableReason` 에 **가장 먼저 걸린 사유 하나**만 들어옵니다.
우선순위는 이 순서입니다:

1. `"AI 매칭에 동의해야 추천 대상에 포함됩니다."`
2. `"매칭을 재개해야 추천 대상에 포함됩니다."`
3. `"이력서를 완성해야 추천 대상에 포함됩니다."`

`matchable` 이 `true` 면 `unmatchableReason` 은 `null` 입니다. 이 문구를 그대로 배너에 노출해 주세요.

> 매칭을 중지해도 **이미 진행 중인 매칭·협상은 그대로 이어집니다.** 안내 문구가 필요합니다.

---

## 3. 클라이언트 마이페이지

### 3-1. 조회

`GET /api/v1/clients/me` · `ROLE_CLIENT`

```json
{
  "accountId": 3,
  "companyName": "주식회사 페어링",
  "businessNo": "1234567890",
  "businessField": "IT_CONTENTS_AI",
  "employeeCount": "SIZE_10_49",
  "email": "owner@pairing.com",
  "name": "홍길동",
  "phone": "01012345678",
  "address": "서울 강남구",
  "grade": "GOLD",
  "ratingAverage": 4.2,
  "reviewCount": 8,
  "withdrawable": true
}
```

- **수정 불가**: `businessNo`, `businessField`, `email`, `name`(담당자명)
- `grade`: `SILVER` | `GOLD` | `DIAMOND`
- ⚠️ `withdrawable` 은 **현재 항상 `true`**

### 3-2. 수정 — 이메일 인증 선행 필요 ⚠️

`PATCH /api/v1/clients/me` · `ROLE_CLIENT`

프리랜서와 동일하게 `purpose: "PROFILE_UPDATE"` 이메일 인증을 먼저 마쳐야 합니다.

```json
{
  "companyName": "주식회사 페어링",
  "employeeCount": "SIZE_10_49",
  "phone": "010-1234-5678",
  "address": "서울 강남구"
}
```

| 필드 | 필수 |
|---|---|
| `companyName` | ✅ (100자 이하) |
| `employeeCount` | ✅ |
| `phone` | |
| `address` | (255자 이하) |

미인증 시 400 `AU_006` 입니다.

---

## 3-3. 결제수단 (프리랜서 · 클라이언트 공통)

경로가 `/api/v1/accounts/**` 라서 계정 도메인처럼 보이지만, 마이페이지 **결제수단 탭**이 쓰는 API 입니다.

가입 시 **카드 1건 + 계좌 1건**이 자동으로 만들어집니다.
**신규 등록·삭제 API 는 없고 수정만 있습니다.**

### 조회

`GET /api/v1/accounts/me/payment-methods` · 로그인 필요

배열로 내려옵니다. `methodType` 으로 갈라서 카드 카드/계좌 카드를 그려 주세요.

```json
[
  {
    "paymentMethodId": 300,
    "methodType": "CARD",
    "displayName": "신한카드 **** 5678",
    "cardBrand": "신한카드",
    "cardLast4": "5678",
    "cardHolder": "홍길동",
    "bankName": null,
    "accountLast4": null,
    "accountHolder": null
  },
  {
    "paymentMethodId": 301,
    "methodType": "BANK_ACCOUNT",
    "displayName": "신한은행 **** 6789",
    "cardBrand": null,
    "cardLast4": null,
    "cardHolder": null,
    "bankName": "신한은행",
    "accountLast4": "6789",
    "accountHolder": "홍길동"
  }
]
```

- **종류에 맞지 않는 필드는 `null`** 입니다. 카드 건은 `bank~`/`account~` 가 전부 null, 계좌 건은 `card~` 가 전부 null
- `displayName` 은 서버가 조립해 주는 표시용 문자열입니다(`"신한카드 **** 5678"` / `"신한은행 **** 6789"`).
  그대로 노출하면 되고, 직접 조립하고 싶으면 `bankName` + `accountLast4` 를 쓰세요
- `bankName` 은 저장값이 아니라 `bankCode` 로 매번 찾아 내려줍니다(은행명이 바뀌어도 옛 이름이 남지 않게)
- ⚠️ `cardHolder` 는 **가입 화면에서 받지 않는 값**이라, 마이페이지에서 카드를 한 번 수정하기 전까지
  기존 계정에서는 `null` 로 내려옵니다. null 방어가 필요합니다

### 카드 수정

`PUT /api/v1/accounts/me/payment-methods/card`

```json
{
  "cardBrand": "신한카드",
  "cardNumber": "1234-5678-9123-4567",
  "cardHolder": "홍길동"
}
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `cardBrand` | ✅ | 30자 이하 |
| `cardNumber` | ✅ | `^[0-9-]{13,23}$` — **하이픈 넣어 보내도 됩니다** |
| `cardHolder` | ✅ | 50자 이하 |

- 카드번호는 **숫자만 정규화 → 암호화**되어 저장됩니다. 평문은 DB에 남지 않습니다
- 끝 4자리(`cardLast4`)만 화면 표시용으로 따로 보관됩니다
- 응답은 조회와 같은 `PaymentMethodResponse` 1건입니다

### 계좌 수정

`PUT /api/v1/accounts/me/payment-methods/bank-account`

```json
{
  "bankCode": "088",
  "accountNo": "110-123-456789",
  "accountHolder": "홍길동"
}
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `bankCode` | ✅ | **금융결제원 기관코드 3자리** (`088` 신한, `004` 국민 …) |
| `accountNo` | ✅ | 30자 이하. 하이픈 허용 |
| `accountHolder` | ✅ | 50자 이하 |

계좌번호도 카드와 같이 **숫자만 정규화 → 암호화** 저장되고, 끝 4자리(`accountLast4`)만 따로 남습니다.

> 🚨 **`bankCode` 는 `"SHINHAN"` 같은 이름이 아니라 숫자 코드(`"088"`)입니다.**
> 이름을 보내면 400 `AC_006` 으로 막힙니다. 은행 목록은 `GET /api/v1/meta/banks` 를 쓰세요.

에러 (카드·계좌 공통)

| status | errorCode | 상황 |
|---|---|---|
| 400 | `AC_006` | 지원하지 않는 은행 코드 |
| 400 | `AC_004` | 번호에서 숫자를 4자리도 못 뽑아냄 |
| 404 | `AC_007` | 등록된 결제수단이 없음 |

---

## 4. 등급 · 혜택

### 4-1. 등급 기준표 — **비로그인 조회 가능**

`GET /api/v1/grades?role=FREELANCER`

`role` 은 필수(`CLIENT` | `FREELANCER`). 배열로 내려옵니다.

```json
[{
  "role": "FREELANCER",
  "grade": "SENIOR",
  "label": "시니어",
  "level": 2,
  "promotionCondition": "별점 평균 3점 이상 + 완료 건수 10건 이상",
  "maintenanceCondition": "12개월 내 프로젝트 경험 · 매월 체크",
  "benefits": [{ "label": "프로젝트 등록", "value": "최대 2개" }],
  "feeRate": {
    "depositUnder": 3.00, "depositOver": 2.00,
    "successFeeUnder": 7.00, "successFeeOver": 6.00
  },
  "feeNote": "기본 수수료"
}]
```

- `level` 이 낮을수록 하위 등급 → 정렬에 사용
- `feeRate` 는 % 값. `~Under` 는 1억 미만, `~Over` 는 1억 이상
- 클라이언트 화면은 수수료를 표로, 프리랜서 화면은 문구로 보여주면 됩니다

### 4-2. 내 등급 현황

`GET /api/v1/grades/me` · 로그인 필요 (역할 제한 없음)

```json
{
  "grade": "SILVER",
  "label": "실버",
  "completedProjectCount": 0,
  "ratingAverage": 4.2,
  "nextGrade": "GOLD",
  "nextGradeGuide": "별점 조건은 충족했습니다. 완료 건수 조건은 계약 도메인 구현 후 정확히 표시됩니다.",
  "checkedGuide": "매월 1일 자동 산정"
}
```

- 최고 등급이면 `nextGrade` · `nextGradeGuide` 가 `null`
- ⚠️ `completedProjectCount` 는 **현재 항상 `0`** (계약 도메인 연동 대기)
- ⚠️ `nextGradeGuide` 도 그래서 완료 건수 조건은 안내하지 못하고 별점 조건만 알려줍니다

---

## 5. 리뷰 · 평점

### 5-1. 리뷰 작성

`POST /api/v1/reviews` · 로그인 필요

```json
{
  "contractId": 600,
  "projectId": 1,
  "revieweeAccountId": 300,
  "counterpart": { "score": 5, "content": "일정 준수가 좋았습니다." },
  "site": { "score": 5, "content": "매칭이 빨라 좋았습니다." }
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `contractId` | ✅ | 어느 거래에 대한 평가인지 |
| `projectId` | ✅ | **임시 필드** — 계약 도메인 완성되면 제거 예정 |
| `revieweeAccountId` | ✅ | **임시 필드** — 동일 |
| `counterpart` | ✅ | 상대 평가. `score` 필수(1~5), `content` 선택(500자) |
| `site` | ✅ | 사이트 후기. `score` 필수, `content` 선택 |

- **작성 후 수정·삭제할 수 없습니다.** 프론트에서 확인 문구를 먼저 보여줘야 합니다
- 계약 1건당 1명이 1번만 작성 가능

에러

| status | errorCode | 상황 |
|---|---|---|
| 409 | `RV_001` | 이미 이 계약에 리뷰를 작성함 |
| 400 | `RV_002` | 별점 범위 오류 / 자기 자신에게 리뷰 |

> 🚨 **현재 이 API 는 500(`GLOBAL_001`) 이 발생합니다.** DB 스키마 불일치로 조사 중이며,
> 해결 전까지는 화면 연동을 보류해 주세요. 진행 상황은 백엔드에 문의 바랍니다.

### 5-2. 받은 리뷰 / 작성한 리뷰

`GET /api/v1/reviews/received?page=0&size=10`
`GET /api/v1/reviews/written?page=0&size=10`

`data` 는 페이지 응답이고 `content` 원소는 이 형태입니다.

```json
{
  "reviewId": 900,
  "contractId": 600,
  "projectTitle": "페어링 웹 리뉴얼",
  "reviewerName": "주식회사 페어링",
  "reviewerRole": "CLIENT",
  "score": 5,
  "content": "일정 준수가 좋았습니다.",
  "createdAt": "2026-08-10T07:12:41"
}
```

- `reviewerName` 은 클라이언트면 **기업명**, 프리랜서면 **이름**
- `projectTitle` · `reviewerName` 은 참조가 끊기면 `null` 이 될 수 있습니다 (조회 자체는 성공)

### 5-3. 내 평점 요약

`GET /api/v1/reviews/summary` — 마이페이지 상단 카드용

```json
{ "averageScore": 4.5, "reviewCount": 12, "grade": "SENIOR" }
```

리뷰가 없으면 `averageScore` 는 `null`, `reviewCount` 는 `0` 입니다.

### 5-4. 작성 대기 목록

`GET /api/v1/reviews/pending`

⚠️ **현재 항상 빈 배열 `[]`** 을 반환합니다 (계약·정산 도메인 연동 대기).
화면은 만들어 두되 "작성할 리뷰가 없습니다" 상태로 보이는 게 정상입니다.

### 5-5. [관리자] 사이트 리뷰 관리 · `ROLE_ADMIN`

**요약 카드**

`GET /api/v1/reviews/admin/site-reviews/summary`

```json
{
  "ratingAverage": 4.8,
  "totalCount": 4,
  "thisMonthCount": 1,
  "promotedCount": 2,
  "notPromotedCount": 2,
  "publicCount": 3,
  "scoreDistribution": { "5": 2, "4": 1, "3": 1, "2": 0, "1": 0 }
}
```

`scoreDistribution` 은 **0건인 별점도 키로 포함**되므로 막대그래프를 그대로 그리면 됩니다.

**목록**

`GET /api/v1/reviews/admin/site-reviews?score=&writerRole=&visibility=&promoted=&page=0&size=10`

| 파라미터 | 값 |
|---|---|
| `score` | 1~5 (선택) |
| `writerRole` | `CLIENT` \| `FREELANCER` (선택) |
| `visibility` | `PUBLIC` \| `PRIVATE` (선택) |
| `promoted` | `true` \| `false` (선택) |

`content` 원소

```json
{
  "siteReviewId": 950,
  "writerRole": "CLIENT",
  "writerName": "삼성전자",
  "score": 5,
  "content": "매칭 속도가 빠르고 좋았습니다.",
  "projectTitle": "페어링 웹 리뉴얼",
  "visibility": "PUBLIC",
  "promoted": true,
  "createdAt": "2026-08-01T07:12:41"
}
```

**공개·홍보 설정 변경**

`PUT /api/v1/reviews/admin/site-reviews/{siteReviewId}/visibility`

```json
{ "visibility": "PUBLIC", "promoted": true }
```

둘 다 필수입니다. 없는 ID 면 404 `RV_003`.

---

## 6. 알림

### 6-1. 목록

`GET /api/v1/notifications?unreadOnly=false&page=0&size=20`

`content` 원소

```json
{
  "notificationId": 1100,
  "type": "MATCHING_REQUESTED",
  "title": "매칭 요청이 도착했습니다",
  "content": "페어링 웹 리뉴얼 프로젝트에서 매칭 요청을 보냈습니다.",
  "linkUrl": "/matching/requests/500",
  "read": false,
  "createdAt": "2026-08-10T07:12:41"
}
```

`linkUrl` 은 알림을 눌렀을 때 이동할 **프론트 경로**입니다. 그대로 라우팅해 주세요.

`type` 값

| 그룹 | 값 |
|---|---|
| 매칭 | `MATCHING_RECOMMENDED` `MATCHING_REQUESTED` `MATCHING_ACCEPTED` `MATCHING_REJECTED` |
| 협상 | `NEGOTIATION_STARTED` `NEGOTIATION_PROPOSED` `NEGOTIATION_FAILED` |
| 계약 | `CONTRACT_CREATED` `CONTRACT_SIGNED` `CONTRACT_REJECTED` |
| 정산 | `SETTLEMENT_DUE` |
| 고객지원 | `INQUIRY_ANSWERED` |

아이콘 매핑에 사용하세요.

### 6-2. 안 읽은 수 (헤더 배지)

`GET /api/v1/notifications/unread-count`

```json
{ "unreadCount": 3 }
```

### 6-3. 읽음 / 삭제

| 요청 | 설명 |
|---|---|
| `PUT /api/v1/notifications/{notificationId}/read` | 하나 읽음 |
| `PUT /api/v1/notifications/read-all` | 모두 읽음 |
| `DELETE /api/v1/notifications/{notificationId}` | 하나 삭제 |
| `DELETE /api/v1/notifications` | 모두 삭제 |

에러: 404 `NT_001`(없음), 403 `NT_002`(남의 알림)

### 6-4. 실시간 푸시 (STOMP)

새 알림은 **WebSocket 으로 실시간 push** 됩니다.

- 핸드셰이크: `/ws` (쿠키 인증이 핸드셰이크 시점에 한 번 수행됩니다)
- 구독 경로: `/topic/users/{accountId}/notifications`
- 메시지 본문은 6-1 의 원소와 같은 형태입니다

푸시를 받으면 목록 맨 위에 추가하고 `unreadCount` 를 +1 해 주세요.

---

## 7. 고객지원 — 챗봇

### 7-1. 질의

`POST /api/v1/support/chatbot/questions` · 로그인 필요

```json
{ "question": "착수금 수수료는 언제 결제하나요?" }
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `sessionId` | | **첫 질문이면 넣지 마세요.** 이어서 물을 때만 사용 |
| `question` | ✅ | 500자 이하 |

응답

```json
{
  "sessionId": 1200,
  "question": "착수금 수수료는 언제 결제하나요?",
  "answer": "착수금 수수료는 계약 체결 시점에 발생합니다.",
  "remainingQuota": 9,
  "createdAt": "2026-08-10T07:12:41"
}
```

**연동 흐름**

1. 첫 질문은 `sessionId` 없이 → 응답의 `sessionId` 를 보관
2. 이후 질문은 그 `sessionId` 를 실어 보냄 → 같은 대화로 이어짐
3. `remainingQuota` 를 입력창 옆에 노출 (하루 10회)

에러

| status | errorCode | 상황 |
|---|---|---|
| 404 | `CB_001` | 없는 `sessionId` |
| 403 | `CB_002` | 남의 세션 |
| 429 | `CB_003` | **하루 10회 소진** → 입력창을 막고 1:1 문의를 안내 |
| 502 | `CB_004` | AI 서버 장애 → "잠시 후 다시 시도" 안내 |

> AI 호출이 실패(`CB_004`)하면 **사용량은 차감되지 않습니다.**

### 7-2. 잔여 한도

`GET /api/v1/support/chatbot/quota`

```json
{ "quotaDate": "2026-08-10", "dailyLimit": 10, "usedCount": 1, "remainingCount": 9 }
```

자정에 초기화됩니다.

### 7-3. 대화 이력

`GET /api/v1/support/chatbot/sessions/{sessionId}/messages`

7-1 응답과 같은 형태의 **배열**이 시간순으로 내려옵니다. 남의 세션이면 403 `CB_002`.

### 7-4. 추천 질문 (칩)

`GET /api/v1/support/chatbot/suggested-questions`

문자열 배열이 내려옵니다. 누르면 그 문자열을 그대로 7-1 로 질의하면 됩니다.

⚠️ 현재는 **고정 목록**입니다 (FAQ 조회수 기반 정렬은 미구현).

---

## 8. 고객지원 — 1:1 문의

### 8-1. 등록

`POST /api/v1/support/inquiries` · 로그인 필요

```json
{
  "title": "정산 관련 문의드립니다",
  "content": "성공보수 수수료 결제일이 언제인지 확인 부탁드립니다.",
  "fileIds": [42]
}
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `title` | ✅ | 200자 이하 |
| `content` | ✅ | 2000자 이하 |
| `fileIds` | | `POST /api/v1/files?purpose=INQUIRY_ATTACHMENT` 로 먼저 업로드 |

> 챗봇 이용 여부와 무관하게 언제든 접수할 수 있습니다. (챗봇을 거쳐야 하는 구조가 아닙니다)

### 8-2. 내 문의 목록 / 상세

`GET /api/v1/support/inquiries/mine?status=&page=0&size=10`
`GET /api/v1/support/inquiries/{inquiryId}`

```json
{
  "inquiryNo": "QNA-20260805-0012",
  "inquiryId": 1300,
  "writerName": null,
  "writerRole": null,
  "writerEmail": null,
  "title": "정산 관련 문의드립니다",
  "content": "성공보수 수수료 결제일이 언제인지 확인 부탁드립니다.",
  "status": "ANSWERED",
  "answer": "안녕하세요. 페어링 고객지원입니다. ...",
  "answererName": "페어링 고객지원",
  "answeredAt": "2026-08-06T07:12:41",
  "files": [{
    "fileId": 42, "originalName": "오류화면.png",
    "fileUrl": "https://cdn.../inquiry_attachment/uuid.png"
  }],
  "createdAt": "2026-08-05T07:12:41"
}
```

- `inquiryNo` 는 화면 표시용 문의번호 → 목록에 그대로 노출
- `status`: `PENDING`(답변 대기) | `ANSWERED`(답변 완료)
- 미답변이면 `answer` · `answererName` · `answeredAt` 이 `null`
- ⚠️ `writerName` · `writerRole` · `writerEmail` 은 **관리자 화면에서만 채워집니다.** 사용자 조회에서는 항상 `null`
- 답변 등록 후에는 **수정·삭제 불가**
- 남의 문의면 403 `IQ_002`, 없으면 404 `IQ_001`

### 8-3. [관리자] 문의 관리 · `ROLE_ADMIN`

**요약 카드**

`GET /api/v1/support/admin/inquiries/summary`

```json
{ "totalCount": 4, "pendingCount": 2, "answeredCount": 2, "todayCount": 1 }
```

**목록**

`GET /api/v1/support/admin/inquiries?keyword=&writerRole=&status=&page=0&size=10`

| 파라미터 | 값 |
|---|---|
| `keyword` | 회원명 · 제목 · 문의번호 검색 (선택) |
| `writerRole` | `CLIENT` \| `FREELANCER` (선택) |
| `status` | `PENDING` \| `ANSWERED` (선택) |

이 목록에서는 `writerName` · `writerRole` · `writerEmail` 이 채워집니다.

**답변 등록**

`POST /api/v1/support/admin/inquiries/{inquiryId}/answer`

```json
{ "answer": "안녕하세요. 페어링 고객지원입니다. ..." }
```

`answer` 는 필수이고 2000자 이하입니다.
답변하면 상태가 `ANSWERED` 로 바뀌고 **사용자에게 알림(`INQUIRY_ANSWERED`)이 발송**됩니다.

---

## 9. 파일 업로드

### 9-1. 업로드

`POST /api/v1/files` · `multipart/form-data` · 로그인 필요

| 파트 | 설명 |
|---|---|
| `file` | 파일 본문 |
| `purpose` | 쿼리 파라미터 또는 폼 필드 (아래 표) |

```json
{
  "fileId": 1,
  "originalName": "portfolio.pdf",
  "fileUrl": "https://cdn.../portfolio/uuid.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 1048576
}
```

**`purpose` 별 제한** — 확장자·용량이 다릅니다.

| purpose | 용량 | 허용 확장자 |
|---|---|---|
| `PROFILE_IMAGE` | 5MB | jpg, jpeg, png |
| `COMPANY_LOGO` | 5MB | jpg, jpeg, png |
| `PORTFOLIO` | 100MB | **pdf 만** |
| `PROJECT_FILE` | 100MB | pdf, jpg, jpeg, png |
| `SIGNATURE` | 5MB | jpg, jpeg, png |
| `INQUIRY_ATTACHMENT` | 10MB | pdf, jpg, jpeg, png |

**업로드 → `fileId` 를 다른 API 요청에 넣는 2단계 흐름**입니다.
이력서·문의 등에는 파일 본문을 직접 보내지 않습니다.

에러

| status | errorCode | 상황 |
|---|---|---|
| 400 | `GLOBAL_008` | 허용되지 않는 확장자 |
| 400 | `FI_003` | 용량 초과 |
| 500 | `GLOBAL_007` | 업로드 실패 |

### 9-2. 메타 조회 / 삭제

`GET /api/v1/files/{fileId}`
`DELETE /api/v1/files/{fileId}` — **업로드한 본인만** 삭제 가능 (403 `FI_002`)

없으면 404 `FI_001`.

---

## 10. 비로그인 메인

전부 **인증 없이** 호출 가능합니다.

### 10-1. 노출 리뷰 ✅

`GET /api/v1/home/site-reviews?size=6`

관리자가 **공개 + 홍보 활용**으로 설정한 **4점 이상** 리뷰만 내려옵니다.
`writerName` 은 `"고**"` 처럼 **마스킹**되어 옵니다.

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `size` | `6` | 가져올 개수 |

응답은 5-5 의 `SiteReviewResponse` **배열**입니다 (페이지 응답이 아닙니다).

### 10-2. 메인 지표 ⚠️

`GET /api/v1/home/summary`

```json
{
  "completionRate": 99.0,
  "projectCount": 5657,
  "negotiationCount": 6000,
  "freelancerCount": 5000,
  "satisfaction": 4.8,
  "totalProjectAmount": 300000000000
}
```

⚠️ **현재 하드코딩된 고정값**입니다 (집계는 프로젝트·협상 도메인 연동 대기).
숫자 자체는 실제 데이터가 아니므로 참고만 하세요.

### 10-3. 자주 찾는 질문 ⚠️

`GET /api/v1/home/faqs`

```json
[{ "question": "...", "answer": "...", "sortOrder": 1 }]
```

⚠️ 현재는 **고정 문구**입니다. `sortOrder` 순으로 정렬해 노출해 주세요.

---

## 11. 미구현 · 제약 요약

프론트에서 **화면은 만들되 데이터가 비어 보이는 게 정상**인 항목입니다.

| API | 현재 상태 | 해제 조건 |
|---|---|---|
| `GET /reviews/pending` | 항상 빈 배열 | 계약·정산 도메인 |
| `GET /grades/me` 의 `completedProjectCount` | 항상 `0` | 계약 도메인 |
| 마이페이지 `withdrawable` (프리랜서·클라이언트) | 항상 `true` | 정산 도메인 |
| `GET /home/summary` | 하드코딩 고정값 | 프로젝트·협상 도메인 |
| `GET /home/faqs` | 고정 문구 | FAQ 관리 기능 |
| `GET /chatbot/suggested-questions` | 고정 목록 | FAQ 조회수 집계 |
| 결제수단 조회의 `cardHolder` | 기존 계정은 `null` | 마이페이지에서 카드를 한 번 수정하면 채워짐 |
| **`POST /reviews`** | **500 발생 중** | DB 스키마 조사 후 수정 (연동 보류) |

---

## 12. 자주 헷갈리는 것 3가지

1. **마이페이지 수정에는 이메일 인증이 필요합니다.** 비밀번호 확인이 아닙니다.
   `purpose: "PROFILE_UPDATE"` 로 인증 → 수정 순서이고, 인증은 **1회용**입니다.

2. **파일은 2단계입니다.** `POST /files` 로 `fileId` 를 받고, 그 숫자를 다른 요청 본문에 넣습니다.
   요청 본문에 파일을 직접 넣는 API 는 없습니다.

3. **이력서 `links` 는 요청과 응답 형태가 다릅니다.**
   요청은 `[{ "url": "..." }]`, 응답은 `["..."]` 입니다.

---

*작성일 2026-08-10 · 담당: 마이페이지 · 등급 · 리뷰 · 알림 · 고객지원 · 파일 도메인*
