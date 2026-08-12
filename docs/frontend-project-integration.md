# 프론트엔드 연동 가이드 — 프로젝트 (클라이언트)

프로젝트 **등록 → 착수금 결제 → 내 프로젝트 목록 → 프로젝트 상세** 까지의 연동 문서입니다.
**피그마 화면 하나를 붙이는 중이라면 [frontend-screen-api-guide.md](frontend-screen-api-guide.md)
를 먼저 보세요.** 화면 → API → 필드가 정리돼 있고, 이 문서는 그 안에서 참조하는 심화편입니다.

계정·인증 공통 규약은 [frontend-auth-integration.md](frontend-auth-integration.md) 를,
그 외 도메인은 [frontend-integration.md](frontend-integration.md) 를 참고하세요.

- 이 문서에 없는 화면(추천 후보 · 협상 · 계약 · 진행 현황 탭)은 **매칭 / 협상 / 계약 도메인** 담당입니다.
- 프로젝트 API 는 **전부 `ROLE_CLIENT` 전용**입니다. 프리랜서 계정으로 호출하면 403 입니다.
  등록 위저드는 1~5단계를 다 채우고 마지막에 403 을 맞게 되므로 **화면 진입 시점에 막아주세요.**

---

## 1. 공통 규약

### 응답 봉투

성공

```json
{
  "timestamp": "2026-08-09T07:12:41.412301200Z",
  "status": 200,
  "code": "PROJECT_FOUND",
  "message": "조회에 성공했습니다.",
  "data": { }
}
```

실패

```json
{
  "timestamp": "2026-08-09T06:45:15.734556900Z",
  "status": 400,
  "errorCode": "PJ_006",
  "message": "현재 상태에서는 처리할 수 없습니다.",
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
| --- | --- |
| 금액 | **원 단위**. 화면이 만원 단위면 `×10,000` 해서 보냅니다 |
| 날짜 | `LocalDate` = `"2026-09-01"`. **`toISOString()` 금지** (3-2 참고) |
| 시각 | `LocalDateTime` = `"2026-07-31T09:12:00"`. 타임존 없음 |
| enum | 요청·응답 모두 **코드**로 주고받습니다 (`"BACKEND"`). 라벨 변환은 meta API |

---

## 2. 선택지 조회 (meta)

화면 진입 시 1회 호출하고 캐싱하세요. 검색 파라미터는 없습니다.

```
GET /api/v1/meta/job-categories    직군      DEVELOPMENT / DESIGN
GET /api/v1/meta/job-roles         직무 26개  parentCode 로 직군 필터
GET /api/v1/meta/skills            스킬 63개  전체를 받아 클라이언트에서 필터링
GET /api/v1/meta/work-conditions   workStyles · workForms · periodUnits
```

응답 항목은 `{ code, label, parentCode }` 형태입니다.

```json
{ "code": "BACKEND", "label": "백엔드 개발자", "parentCode": "DEVELOPMENT" }
```

`work-conditions` 는 5개 묶음이 한 번에 오는데 **프로젝트 등록에서 쓰는 건 3개**입니다.
`payUnits`(시급·일급·월급)와 `skillLevels`(초급·중급·고급)는 프리랜서 화면용이라 무시하세요.

---

## 3. 등록 위저드 (6단계)

**5단계 검수를 제외하면 1~5단계는 API 를 호출하지 않습니다.** 입력값을 프론트 상태로 들고 있다가
6단계에서 `POST /api/v1/projects` 한 번으로 전송합니다.

| 단계 | 화면 | 호출 |
| --- | --- | --- |
| 1 | 등록 안내 | 없음. 동의 체크값만 상태 보관 |
| 2 | 기본 정보 | `GET /meta/work-conditions` |
| 3 | 직군 모집 | `GET /meta/job-categories` `/job-roles` `/skills` |
| 4 | 상세 정보 | `POST /files` · `DELETE /files/{fileId}` (첨부만) |
| 5 | 검수 | `POST /projects/pre-review` |
| 6 | 최종 확인 | **`POST /projects`** ← 실제 등록 |

### 3-1. 1단계 — 등록 안내

동의 체크값은 별도 API 없이 **6단계 본문의 `noticeAgreed`** 로 함께 보냅니다.
약관 ID 도 enum 도 없습니다. 동의 시각은 서버가 자동 기록하며 응답에 내려오지 않습니다.

미체크로 보내면 400 입니다.

```json
{ "status": 400, "errorCode": "GLOBAL_002",
  "message": "noticeAgreed: 등록 전 안내에 동의해야 합니다." }
```

**수정(PUT)할 때는 받지 않습니다.**

### 3-2. 2단계 — 기본 정보

전송 필드: `title` `startDesiredDate` `startNegotiable` `periodValue` `periodUnit`
`budgetAmount` `workStyle` `workForm`

**⚠ 예산은 화면이 만원 단위, API 는 원 단위입니다.**

```
화면 3,000 만원  →  budgetAmount: 30000000
최소 5000000 · 최대 1000000000
```

**⚠ 시작 희망일은 `"2026-09-01"` 형식만 받습니다.**

```
보내야 함   "2026-09-01"
잘못 보냄   "2026-09-01T00:00:00.000Z"     ← 400
```

`Date.toISOString()` 은 UTC 로 변환하므로 **KST 오전 9시 이전에 오늘 날짜를 고르면 전날**이 되어
`"시작 희망일은 오늘 이후여야 합니다"` 400 이 납니다. `<input type="date">` 의 `value` 를 그대로
쓰거나 `getFullYear()`·`getMonth()+1`·`getDate()` 로 직접 조립하세요.

**시작 희망일과 협의 가능은 서로 독립입니다.** 둘 다 채워 보내도 정상 등록됩니다.
협의 가능을 체크해도 `startDesiredDate` 는 반드시 보내야 합니다(필수 필드).

**근무 장소는 입력받지 않습니다.** `workStyle` 이 `ONSITE` 면 서버가 클라이언트 회원가입 때 받은
주소를 자동으로 복사해 저장합니다. 요청 필드에 넣지 마세요. 응답(`workLocation`)에만 내려옵니다.
등록·수정 시점에 복사하는 값이라, 마이페이지에서 주소를 바꿔도 기존 프로젝트에는 반영되지 않습니다.

### 3-3. 3단계 — 직군 모집

포지션 배열을 만듭니다. 직군(`jobCategory`)을 고르면 `parentCode` 로 직무 드롭다운을 필터합니다.

| 항목 | 제약 |
| --- | --- |
| 모집 직군 | 1~100건 |
| 희망 경력 | 1~50 (**0=신입 불가**) |
| 모집 인원 | 1~50 |
| 요구 스킬 | 1개 이상, 63개 이하 |

### 3-4. 4단계 — 상세 정보

| 화면 | 필드 | 필수 | 길이 |
| --- | --- | --- | --- |
| 현재 프로젝트 진행 상황 | `currentSituation` | ✅ | 1500자 |
| 주요 담당 업무 | `mainTask` | ✅ | 1500자 |
| 세부 업무 범위 | `detailScope` | | 1500자 |
| 기타 전달사항 및 우대사항 | `extraNote` | | 1500자 |

작성 가이드·예시 아코디언·글자수 카운터는 전부 프론트 고정 문구입니다.

**첨부 파일**

```
POST   /api/v1/files?purpose=PROJECT_FILE     multipart, part 이름 "file"
DELETE /api/v1/files/{fileId}                 X 버튼
```

응답의 `fileId` 를 모아 6단계에서 `fileIds: [1, 2]` 로 보냅니다.
제한은 `pdf/jpg/jpeg/png` · 파일당 100MB · 최대 10개이고, 10개가 차면 업로드 박스를 비활성화하세요.
파일 크기 표기(`29491` → `"28.8 KB"`)는 프론트에서 변환합니다.

### 3-5. 5단계 — 사전 검수

```
POST /api/v1/projects/pre-review
```

3단계에서 입력한 **직무·인원·스킬만** 보냅니다. 직군·경력·예산은 검수에 쓰지 않습니다.

```json
{
  "positions": [
    { "jobRole": "FRONTEND", "headcount": 2, "skills": ["REACT", "TYPESCRIPT"] },
    { "jobRole": "BACKEND",  "headcount": 1, "skills": ["JAVA", "SPRING_BOOT"] }
  ]
}
```

응답 (`code: "PRE_REVIEW_DONE"`)

```json
{
  "allMatchable": false,
  "items": [
    { "positionIndex": 0, "jobRole": "FRONTEND", "headcount": 2,
      "expectedCandidateCount": 5, "matchable": true,
      "message": null, "suggestions": [] },
    { "positionIndex": 1, "jobRole": "BACKEND", "headcount": 1,
      "expectedCandidateCount": 0, "matchable": false,
      "message": "현재 조건에 맞는 백엔드 개발자 후보가 없습니다.",
      "suggestions": [
        "요구 스킬을 줄이면 더 많은 후보를 확인할 수 있습니다.",
        "직무를 추가하거나 다른 직무로 변경해보세요."
      ] }
  ],
  "notice": "현재 프리랜서 풀 기준 예상 결과입니다. 실제 후보 수 및 매칭 성사 여부는 달라질 수 있습니다."
}
```

| 필드 | 화면 |
| --- | --- |
| `positionIndex` | 요청 배열 순서(0-based). **카드 매핑은 이 값으로** |
| `jobRole` | 코드입니다. 카드 제목은 `/meta/job-roles` 로 label 변환 |
| `headcount` | "모집 인원 2명" |
| `expectedCandidateCount` | "예상 후보 5명" |
| `matchable` | `true` 초록 체크 + "매칭 가능" / `false` 빨간 느낌표 + "후보 부족" |
| `message` | 빨간 박스 첫 줄. `matchable` 이면 `null` |
| `suggestions` | 빨간 박스 불릿. `matchable` 이면 빈 배열 |
| `notice` | 상단 안내 문구. 그대로 출력 |

**같은 직무를 여러 포지션으로 모집할 수 있으므로 `jobRole` 로 묶으면 안 됩니다.**
백엔드 1명 + 백엔드 1명이면 카드가 2장 나오고, 두 카드 모두 같은 후보 수를 보여줍니다(중복 계산).

**`message` 와 `suggestions` 는 서버 값을 그대로 출력하세요.** 상황에 따라 문구가 갈립니다.

```
후보 0명      "현재 조건에 맞는 백엔드 개발자 후보가 없습니다."
후보 1명 이상  "현재 조건에 맞는 백엔드 개발자 후보가 모집 인원보다 부족합니다."
```

`suggestions` 도 마찬가지입니다. 요구 스킬이 1개뿐이면 "요구 스킬을 줄이면" 문구가 빠지고,
후보가 1명 이상이면 "모집 인원을 N명으로 줄이면 매칭을 시작할 수 있습니다" 가 대신 들어갑니다.

**버튼 3개는 API 를 호출하지 않습니다.**

| `allMatchable` | 버튼 |
| --- | --- |
| `true` | `[등록 수정]` `[입력한 내용으로 등록하기]` |
| `false` | `[등록 수정]` `[등록 취소]` `[입력한 내용으로 등록하기]` |

- **등록 수정** — "직군 모집 단계로 이동합니다" 안내 후 3단계로. 입력 상태 유지
- **등록 취소** — "정말로 취소하겠습니까?" 확인 후 홈으로.
  **입력 폼 폐기이지 프로젝트 취소 API 가 아닙니다**
- **입력한 내용으로 등록하기** — 6단계로 이동

### 3-6. 6단계 — 최종 확인 · 등록

```
POST /api/v1/projects
```

```json
{
  "title": "쇼핑몰 관리자 페이지 리뉴얼",
  "positions": [
    { "jobCategory": "DEVELOPMENT", "jobRole": "FRONTEND",
      "minCareerYears": 3, "headcount": 2,
      "skills": ["REACT", "TYPESCRIPT", "NEXT_JS"] },
    { "jobCategory": "DEVELOPMENT", "jobRole": "BACKEND",
      "minCareerYears": 5, "headcount": 1,
      "skills": ["JAVA", "SPRING_BOOT", "MYSQL"] }
  ],
  "startDesiredDate": "2026-09-01",
  "startNegotiable": true,
  "periodValue": 2,
  "periodUnit": "MONTH",
  "budgetAmount": 30000000,
  "workStyle": "REMOTE",
  "workForm": "FULL_TIME",
  "currentSituation": "...",
  "mainTask": "...",
  "detailScope": "...",
  "extraNote": "...",
  "fileIds": [1, 2],
  "noticeAgreed": true
}
```

`201 Created` · `code: "PROJECT_CREATED"` · `data` 는 아래 `ProjectResponse` 입니다.
등록과 동시에 **착수금 정산이 자동 생성**되어 `payableSettlementId` 가 채워집니다.

---

## 4. 등록 완료 화면

`POST /api/v1/projects` 의 응답만으로 그립니다. **추가 호출이 없습니다.**

| 화면 | 필드 |
| --- | --- |
| 프로젝트명 | `title` |
| 프로젝트 기간 | `periodValue` + `periodUnit` → `"4개월"` |
| 예산 | `budgetAmount` (원 단위, 콤마 포맷) |
| 시작 희망일 | `startDesiredDate` |
| 현재 상태 배지 | `status` = `REGISTERED` → "등록 완료" |
| 모집 포지션 | `positions[]` 의 `jobRole` + `headcount` → `"프론트엔드×2, 백엔드×1"` |
| `[착수금 결제하기]` | `payableSettlementId` 를 결제 모달에 넘김 |

`positions[].jobRole` 은 코드이므로 `/meta/job-roles` 로 label 변환이 필요합니다.

---

## 5. 결제 모달 (착수금 · 성공보수 공용)

착수금과 성공보수가 **같은 모달·같은 API** 를 씁니다. 어느 쪽인지는 `phase` 로 구분합니다.

### ① 결제 건 조회 — 모달 열릴 때

```
GET /api/v1/settlements/{settlementId}
```

`settlementId` 는 프로젝트의 `payableSettlementId` 를 그대로 넘깁니다.

```json
{
  "settlementId": 3,
  "settlementNo": "ST-2026-000003",
  "projectId": 6,
  "projectTitle": "쇼핑몰 관리자 페이지 리뉴얼",
  "phase": "DEPOSIT",
  "baseAmount": 80000000,
  "feeRate": 3,
  "gradeDiscount": 0,
  "feeAmount": 2400000,
  "status": "PENDING",
  "payable": true,
  "paymentMethodLabel": null,
  "approvalNo": null,
  "paidAt": null
}
```

| 필드 | 화면 |
| --- | --- |
| `projectTitle` + `phase` | `"쇼핑몰 관리자 페이지 리뉴얼 · 착수금 수수료"` |
| `feeAmount` | 상단 금액과 하단 버튼 금액. **원 단위** |
| `payable` | `false` 면 결제 버튼 비활성화 |
| `projectId` | 결제 후 프로젝트 상태 재조회에 사용 |

`phase` 는 `DEPOSIT`(착수금) / `SUCCESS_FEE`(성공보수)입니다.

**⚠ `baseAmount` 는 프로젝트 예산이고, 실제 결제 금액은 `feeAmount` 입니다.**
예산 8,000만 원의 착수금 수수료 3% = 240만 원이 결제됩니다. 혼동하지 마세요.

`paymentMethodLabel` 은 현재 항상 `null` 입니다(미구현). 결제한 카드를 표시하려면
`paymentMethodId` 로 결제수단을 따로 조회해야 합니다.

### ② 결제수단 조회

```
GET /api/v1/accounts/me/payment-methods
```

카드 1건 + 계좌 1건이 함께 옵니다. **`methodType === "CARD"` 만 필터**하세요
(`BANK_ACCOUNT` 는 용역비 수령용입니다).

| 필드 | 비고 |
| --- | --- |
| `paymentMethodId` | 결제 요청에 넣을 ID |
| `displayName` | `"신한카드 **** 1234"` — 그대로 출력 |
| `cardBrand` | 회원가입 때 사용자가 입력한 **자유 문자열**(최대 30자). 코드값이 없어 로고 매핑 불가 |
| `cardLast4` | 뒤 4자리. 앞자리는 저장하지 않음 |
| `cardHolder` | **항상 `null`** |

**⚠ 시안의 `1234-****-****-5678` 형식은 API 로 만들 수 없습니다.** 앞 4자리를 저장하지 않으므로
`displayName` 형식(`신한카드 **** 1234`)으로 맞춰주세요.

**⚠ 카드는 계정당 1장입니다.** 추가·삭제 API 가 없고 수정(PUT)만 있어서, 목록에서 고르는 UI 는
실질적으로 1건만 표시됩니다.

### ③ 결제 실행

```
POST /api/v1/settlements/{settlementId}/payment
```

```json
{ "paymentMethodId": 1 }
```

성공하면 갱신된 정산 1건이 옵니다 (`code: "SETTLEMENT_PAID"`).

```
status      PAID
approvalNo  "AP-20260809-0003"
paidAt      결제 시각
```

실패 시 `status = FAILED`, `failReason` 에 사유가 담깁니다.

### ④ 결제 완료 화면

**결제 응답에 프로젝트 상태가 없습니다.** 모달 응답의 `projectId` 로 다시 조회해서 표시하세요.

```
GET /api/v1/projects/{projectId}   →  status: "RECRUITING"
```

`[내 프로젝트 보기]` 는 `GET /api/v1/projects/mine?tab=MATCHING` 으로 이동합니다.

---

## 6. 내 프로젝트 목록

```
GET /api/v1/projects/mine?tab=REGISTERED&page=0&size=10
```

`tab` 을 비우면 전체 상태가 섞여 나옵니다. `page` 기본 0, `size` 기본 10.

탭 6개는 고정입니다. 별도 조회 API 없이 박아 쓰면 됩니다.

```js
const TABS = [
  { tab: 'REGISTERED',         label: '등록 완료' },
  { tab: 'MATCHING',           label: '매칭 중' },
  { tab: 'IN_PROGRESS',        label: '진행 중' },
  { tab: 'COMPLETION_PENDING', label: '완료 대기' },
  { tab: 'CLOSED',             label: '종료' },
  { tab: 'CANCELED',           label: '취소됨' },
];
```

**⚠ `매칭 중` 탭 하나가 `모집중 + 협상중 + 계약대기` 세 상태를 묶습니다.**
그래서 같은 탭 안에서 카드 배지가 서로 다를 수 있습니다. 정상입니다.
"모집 중" "협상 중" "협상 완료" 같은 **별도 탭은 서버에 없습니다.**

### 카드 (`content[]` 한 건)

| 필드 | 화면 | 비고 |
| --- | --- | --- |
| `projectId` | 상세 이동 | |
| `title` | 제목 | |
| `status` | 배지 | **코드**로 옴 |
| `jobRoleLabels[]` | 직무 칩 | **라벨**로 옴. 변환 불필요 |
| `skillLabels[]` | 스킬 칩 | **라벨**로 옴 |
| `budgetAmount` | `"예산 15,000,000원"` | 원 단위 |
| `periodLabel` | `"기간 3개월"` | 조립된 문자열 |
| `startDesiredDate` | `"시작일 2026.08.15"` | |
| `totalHeadcount` | `"인원 2명"` | |
| `recruitDeadline` | `"마감일 D-5"` | **`null` 이면 숨김**(모집 시작 전) |
| `createdAt` | `"등록일 2026.07.31"` | |
| `payableSettlementId` | 결제 버튼 | `null` 이면 버튼 숨김 |

**상태는 코드로, 직무·스킬은 라벨로 옵니다. 섞여 있으니 주의하세요.**

포지션이 여러 개면 `jobRoleLabels` 는 중복 제거된 직무 전체, `skillLabels` 는 전 포지션 스킬을
합쳐 중복 제거한 목록입니다. 칩이 많아질 수 있으니 화면에서 잘라 쓰세요.

**항상 `null` 인 필드** — `statusNote` `clientName` `matchedFreelancerName` 은 내 목록에서
채우지 않습니다(관리자 목록·매칭 도메인 값). `projectNo`(`"PRJ-000001"`)·`paymentStatus`·
`confirmedHeadcount` 는 값이 오지만 현재 시안에서는 쓰지 않습니다.

### 상태 배지 매핑

```
REGISTERED          등록 완료
RECRUITING          모집중
NEGOTIATING         협상중
CONTRACT_PENDING    계약 대기
IN_PROGRESS         진행중
COMPLETION_PENDING  완료 대기
CLOSED              종료
CANCELED            취소됨
```

### 탭별 카드 버튼

| 탭 | 버튼 |
| --- | --- |
| 등록 완료 | `[공고 확인하기]` `[결제 하기]` ← 착수금 |
| 매칭 중 | `[상세 보기]` |
| 진행 중 | `[상세 보기]` `[프로젝트 완료]` |
| 완료 대기 | `[상세 보기]` `[성공 수수료 결제]` ← 성공보수 |
| 종료 · 취소됨 | `[상세 보기]` |

결제 버튼은 둘 다 `payableSettlementId` 를 결제 모달에 넘깁니다. **모달 내부 API 는 동일**하고
문구만 상태로 가릅니다.

---

## 7. 프로젝트 상세

```
GET /api/v1/projects/{projectId}
```

본인 프로젝트만 조회할 수 있습니다(아니면 `PJ_003`).

### 프로젝트 정보 탭

```json
{
  "projectId": 6,
  "title": "B2B 주문 관리 서비스 리뉴얼",
  "status": "RECRUITING",
  "statusNote": null,
  "paymentStatus": "DEPOSIT_PAID",
  "startDesiredDate": "2026-09-01",
  "startNegotiable": true,
  "periodValue": 4,
  "periodUnit": "MONTH",
  "budgetAmount": 60000000,
  "workStyle": "REMOTE",
  "workForm": "FULL_TIME",
  "workLocation": null,
  "currentSituation": "...",
  "mainTask": "...",
  "detailScope": "...",
  "extraNote": "...",
  "totalHeadcount": 3,
  "confirmedHeadcount": 0,
  "recruitDeadline": "2026-08-23T16:08:19",
  "extensionCount": 0,
  "freeRerecommendUsed": 0,
  "paidRerecommendUsed": 0,
  "positions": [
    { "positionId": 6, "positionNo": 1, "jobCategory": "DEVELOPMENT",
      "jobRole": "BACKEND", "minCareerYears": 3, "headcount": 2,
      "confirmedCount": 0, "status": "RECRUITING",
      "skills": ["JAVA", "SPRING_BOOT"] }
  ],
  "files": [
    { "fileId": 1, "originalName": "기획서.pdf",
      "sizeBytes": 29491, "fileUrl": "https://..." }
  ],
  "createdAt": "2026-08-09T16:08:19",
  "payableSettlementId": 3
}
```

| 화면 | 필드 |
| --- | --- |
| 헤더 등록일·시작희망일·기간·전체 모집 | `createdAt` `startDesiredDate` `periodValue+Unit` `totalHeadcount` |
| 마감일 D-N | `recruitDeadline`. **`null` 이면 숨김** |
| `0 / 2회 사용` | `extensionCount` / 2 |
| 기본 정보 카드 | `budgetAmount` `periodValue+Unit` `startDesiredDate` `workStyle` |

`positions[]` 의 `jobCategory` `jobRole` `skills` `status` 는 **코드**입니다. meta 로 변환하세요.
`workLocation` 은 `ONSITE` 일 때만 값이 있습니다.

**⚠ `ProjectResponse` 에 `freelancers` 필드는 없습니다.** 프리랜서 현황은 매칭 API 입니다.

### 프리랜서 현황 — 매칭 API

```
GET /api/v1/matchings/requests?projectId={projectId}&size=100
```

`projectId` 는 URL 값을 그대로 넣습니다. **안 넣으면 내 프로젝트 전체가 섞입니다.**
남의 프로젝트면 403, 클라이언트 전용입니다.

`content[]` 로 카드를 그립니다.

| 필드 | 화면 |
| --- | --- |
| `counterpartName` | `"김개발"` — 클라이언트가 보면 프리랜서명 |
| `jobRole` | 코드. `/meta/job-roles` 로 변환 |
| `status` | 배지 (`NEGOTIATING` → "협상중") |
| `negotiationId` | 수락 전이면 `null` → `[협상 하기]` 노출 여부 |
| `currentRound` / `maxRound` / `newProposalCount` | 협상 진행도 |

### 상태별 버튼

| 상태 | 버튼 |
| --- | --- |
| 등록 완료 | 수정 · 등록 취소 · 착수금 결제 |
| 모집중 | 수정 · 모집 연장 · 모집 종료 |
| 협상중 · 계약 대기 | 수정 |
| 진행중 | 수정 · 프로젝트 완료 |
| 완료 대기 | 수정 · 성공보수 결제 |
| 종료 · 취소됨 | 없음 |

```
수정          PUT  /api/v1/projects/{projectId}
등록 취소      POST /api/v1/projects/{projectId}/registration-cancellation   → 취소됨
모집 연장      POST /api/v1/projects/{projectId}/recruit-extensions          → 마감일 +1주
모집 종료      POST /api/v1/projects/{projectId}/recruit-close               → 취소됨
프로젝트 완료   POST /api/v1/projects/{projectId}/completion                  → 완료 대기
결제 (둘 다)   POST /api/v1/settlements/{payableSettlementId}/payment
```

버튼 노출 규칙

- **결제 버튼은 `payableSettlementId != null` 일 때만** 노출합니다. 등록 완료면 착수금,
  완료 대기면 성공보수를 가리킵니다. 문구만 상태로 가릅니다.
- **등록 취소는 등록 완료 상태에서만** 노출합니다. 착수금 결제 후에는 숨깁니다(`PJ_015`).
- **연장 버튼은 `extensionCount >= 2` 면 비활성화**합니다.
- **마감일 D-N 은 `recruitDeadline` 이 `null` 이면 숨깁니다**(모집 시작 전).

### 수정 (PUT)

등록과 같은 본문에서 `noticeAgreed` 를 빼고, `positions[]` 에 `positionId` 를 추가한 형태입니다.

```json
{
  "positions": [
    { "positionId": 6, "jobCategory": "DEVELOPMENT", "jobRole": "BACKEND",
      "minCareerYears": 5, "headcount": 3, "skills": ["JAVA", "SPRING_BOOT"] },
    { "positionId": null, "jobCategory": "DEVELOPMENT", "jobRole": "DEVOPS",
      "minCareerYears": 4, "headcount": 1, "skills": ["AWS", "DOCKER"] }
  ]
}
```

- `positionId` 있음 → 기존 포지션 **수정**
- `positionId` `null` → **추가**
- 목록에 없는 기존 포지션 → **삭제**

**부분 수정이 아니라 전체 교체입니다.** 바꾸지 않는 값도 전부 실어 보내야 합니다.
`GET` 으로 현재 값을 받아 채운 뒤 변경분만 덮어쓰는 방식이 안전합니다.

착수금 결제 후에는 세 항목이 잠깁니다.

```
인원 변경        PJ_007
포지션 추가·삭제  PJ_008
예산 변경        PJ_011
```

제목 · 직군 · 직무 · 경력 · 스킬 · 근무조건 · 기간 · 시작일 · 텍스트 · 첨부는 계속 수정 가능합니다.

**결제 전에 예산을 바꾸면 착수금 정산이 자동으로 재계산됩니다.** 결제 모달을 다시 열면 새 금액이
반영되어 있습니다.

---

## 8. 에러 코드

| 코드 | 상태 | 의미 | 화면 처리 |
| --- | --- | --- | --- |
| `PJ_001` | 404 | 프로젝트 없음 | 목록으로 |
| `PJ_002` | 404 | 모집 직군 없음 | 수정 시 잘못된 `positionId` |
| `PJ_003` | 403 | 본인 프로젝트 아님 | 목록으로 |
| `PJ_004` | 400 | 프로젝트 정보 오류 | 해당 입력칸 표시 |
| `PJ_005` | 400 | 모집 직군 정보 오류 | 해당 카드 표시 |
| `PJ_006` | 400 | 현재 상태에서 처리 불가 | 상세 재조회 후 버튼 갱신 |
| `PJ_007` | 400 | 결제 후 인원 변경 불가 | 인원 입력 잠금 |
| `PJ_008` | 400 | 결제 후 포지션 추가·삭제 불가 | 추가·삭제 버튼 잠금 |
| `PJ_009` | 400 | 확정 인원보다 적게 줄일 수 없음 | 인원 최솟값 안내 |
| `PJ_010` | 400 | 연장 3회차 | 연장 버튼 비활성화 |
| `PJ_011` | 400 | 결제 후 예산 변경 불가 | 예산 입력 잠금 |
| `PJ_012` | 400 | 취소·종료된 프로젝트 | 버튼 숨김 |
| `PJ_013` | 400 | 이미 마감된 직군 | |
| `PJ_014` | 400 | 모집중이 아님(모집 종료) | 상세 재조회 |
| `PJ_015` | 400 | 착수금 결제 후 등록 취소 불가 | 등록 취소 버튼 숨김 |
| `ST_001` | 404 | 정산 없음 | `payableSettlementId` 확인 |
| `ST_002` | 403 | 납부자 본인 아님 | |
| `ST_003` | 400 | 이미 결제됨 | 상세 재조회 |
| `FI_001` | 404 | 파일 없음 | 첨부 목록 갱신 |
| `GLOBAL_002` | 400 | 형식 검증 실패 | `message` 가 `필드명: 사유` 형식 |
| `GLOBAL_005` | 403 | 권한 없음 | 프리랜서 계정 |
| `GLOBAL_006` | 401 | 미인증 | 로그인 페이지로 |

`PJ_006` ~ `PJ_015` 는 **버튼이 잘못 떴을 때의 방어선**입니다. 정상 흐름에서는 화면이 먼저 막아야
합니다.

---

## 9. 자주 겪는 문제

**등록이 400 인데 원인을 모르겠다**
`message` 를 보세요. `GLOBAL_002` 면 `"필드명: 사유"` 형식으로 어떤 필드인지 나옵니다.

**시작 희망일에서 계속 400 이 난다**
`"2026-09-01"` 형식이 맞는지 확인하세요. `toISOString()` 을 쓰면 `T00:00:00.000Z` 가 붙고,
UTC 변환 때문에 하루가 밀려 "오늘 이후여야 합니다" 가 나올 수 있습니다.
협의 가능을 체크해도 날짜는 그대로 보내야 합니다.

**예산이 1/10000 로 저장된다**
만원 단위를 그대로 보냈습니다. `×10,000` 하세요.

**검수 결과 카드가 엉뚱한 포지션에 붙는다**
`jobRole` 로 매칭하고 있습니다. 같은 직무를 여러 포지션으로 모집할 수 있으므로
반드시 `positionIndex` 로 매핑하세요.

**결제 모달 금액이 프로젝트 예산으로 나온다**
`baseAmount` 를 표시하고 있습니다. 결제 금액은 `feeAmount` 입니다.

**결제 후 프로젝트 상태가 안 바뀐 것처럼 보인다**
결제 응답에는 프로젝트 상태가 없습니다. `GET /projects/{projectId}` 로 다시 조회하세요.

**목록에서 결제 버튼이 안 뜬다**
`payableSettlementId` 가 `null` 입니다. 이미 결제했거나 결제할 정산이 없는 상태입니다.

**"모집 중" 탭이 안 보인다**
`매칭 중` 탭에 포함돼 있습니다. 카드 배지로 구분하세요.

**상세에서 프리랜서 현황이 비어 있다**
`ProjectResponse` 에는 없습니다. `GET /matchings/requests?projectId=` 를 호출해야 합니다.

**수정했더니 안 바꾼 값이 사라졌다**
PUT 은 전체 교체입니다. `positions` 에서 빠진 포지션은 삭제됩니다.

**상주로 등록했는데 근무 장소가 비어 있다**
클라이언트 회원가입 때 받은 주소를 복사하는 값입니다. 주소가 없는 기존 계정이면
`PATCH /api/v1/clients/me` 로 채운 뒤 프로젝트를 다시 저장해야 반영됩니다.

---

## 10. 이 문서에 없는 것

| 화면 | 담당 |
| --- | --- |
| 상세의 추천 후보 · 협상 · 계약 · 진행 현황 탭 | 매칭 · 협상 · 계약 도메인 |
| 계약 관리 메뉴 | 계약 도메인 |
| 관리자 프로젝트 목록·상세 | 아직 스켈레톤 |
| `GET /projects/{projectId}/pre-review` (등록 후 재검수) | 구현돼 있으나 현재 시안에 화면 없음 |
| `GET /projects/mine/tab-counts` (탭 배지 숫자) | 구현돼 있으나 현재 시안에 배지 없음 |
