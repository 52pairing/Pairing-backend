# 마이페이지 프론트 연동 가이드

> 담당: 리뷰·등급·마이페이지·이력서·결제수단·1:1문의·챗봇
> 기준일: 2026-08-11 / 브랜치 `feature/fixFreelancer`
> 와이어프레임·요구사항 명세·정책과 대조해 서버를 맞춘 결과입니다.

---

## 0. 먼저 읽어주세요 — 이전 안내에서 바뀐 것

| # | 무엇이 | 어떻게 |
|---|---|---|
| 1 | 리뷰 작성 요청 | `projectId`, `revieweeAccountId` **제거**. `contractId` 만 보냄 |
| 2 | 리뷰 작성 대기 응답 | 스키마 변경 (`PendingReviewResponse`) |
| 3 | 리뷰 작성 시점 | 성공보수 결제 완료 후에만 가능. 아니면 `409 RV_004` |
| 4 | 클라이언트 기업 로고 | `logoUrl` 조회 / `logoFileId` 수정 **신규 추가** |
| 5 | 희망 급여 | **만원 단위**만 허용 (10,000의 배수, 최소 10,000) |
| 6 | 자기소개 | 2,000자 → **1,500자** (화면 카운터와 일치) |

---

## 1. 공통 규칙

### 인증

모든 API는 **로그인 필요**입니다. HttpOnly 쿠키 `accessToken` 이 자동으로 실려갑니다.
`fetch` 사용 시 `credentials: 'include'` 를 꼭 넣어주세요.

| 상태 | 의미 |
|---|---|
| `401 GLOBAL_006` | 로그인 안 됨 / 토큰 만료 → 로그인 화면 |
| `403 GLOBAL_005` | 역할이 안 맞음 (프리랜서 API를 클라이언트가 호출 등) |

### 응답 형태

```json
{ "code": "MY_PAGE_FOUND", "message": "조회에 성공했습니다.", "data": { } }
```

에러도 같은 껍데기이고 `errorCode` 가 붙습니다.

```json
{ "code": "ERROR", "message": "대금 지급이 완료된 후에 리뷰를 작성할 수 있습니다.", "errorCode": "RV_004" }
```

### 금액 단위

**API는 전부 `원` 단위 정수입니다.** 화면에서 만원으로 보여주고, 보낼 때 `× 10000` 해주세요.

```
화면 입력 500 만원  →  API "payAmount": 5000000
API 5000000        →  화면 "500만원"
```

희망 급여·최저 수용 금액은 **10,000의 배수**여야 합니다. 아니면 `400` 입니다.

---

## 2. 프리랜서 마이페이지

사이드바 7개: 기본 정보 / 이력서·포트폴리오 / 리뷰 관리 / 결제수단 / 수수료 결제 내역 / 매칭 설정 / 회원 탈퇴

> `수수료 결제 내역` 은 정산 도메인(`/api/v1/settlements/me`)입니다. 이 문서 범위 밖입니다.

### 2-1. 기본 정보

```
GET   /api/v1/freelancers/me
PATCH /api/v1/freelancers/me
```

**응답**

```json
{
  "accountId": 12,
  "name": "김개발",
  "email": "kimgaebal@dev.kr",
  "phone": "01012345678",
  "birthDate": "1990-05-20",
  "address": "서울특별시 강남구 테헤란로 123",
  "profileImageUrl": "https://cdn.../profile/xxx.png",
  "aiMatchingAgreed": true,
  "grade": "SENIOR",
  "ratingAverage": 4.8,
  "reviewCount": 17,
  "resumeCompleted": true,
  "withdrawable": true
}
```

| 화면 | 필드 |
|---|---|
| 김개발 | `name` |
| `시니어` 배지 | `grade` |
| ★ 4.8 리뷰 17건 | `ratingAverage`, `reviewCount` |
| 이름·이메일·생년월일 (변경 불가) | `name`, `email`, `birthDate` |
| 전화번호 | `phone` |
| 주소 | `address` |
| 회원 탈퇴 노출 | `withdrawable` |

**수정** — ⚠️ **이메일 인증 선행 필수** (3-1 참고)

```json
PATCH /api/v1/freelancers/me
{
  "profileFileId": 3,
  "phone": "010-1234-5678",
  "address": "서울특별시 강남구 테헤란로 123",
  "aiMatchingAgreed": true
}
```

- `aiMatchingAgreed` 는 **필수**. 나머지는 선택
- `name`, `email`, `birthDate` 는 수정 불가 — 보내도 무시
- `phone` 은 `010-1234-5678` / `01012345678` 둘 다 허용
- `profileFileId` 는 5-1 파일 업로드로 먼저 받습니다

### 2-2. AI 매칭 설정 (토글)

```
GET /api/v1/freelancers/me/matching-settings
PUT /api/v1/freelancers/me/matching-settings
```

```json
{
  "aiMatchingAgreed": true,
  "matchingPaused": false,
  "matchable": true,
  "unmatchableReason": null
}
```

- 토글 ON/OFF → `matchingPaused` 를 뒤집어 `PUT`
- `matchable` = 동의 O + 일시중지 X + 이력서 완료. **실제로 추천 대상인지**
- 꺼져 있으면 `unmatchableReason` 문구를 그대로 보여주세요
- "현재 진행 중인 요청과 협상에는 영향을 주지 않습니다" 는 정적 문구

### 2-3. 다음 등급까지

```
GET /api/v1/grades/me              내 현황
GET /api/v1/grades?role=FREELANCER 등급 기준표(목표치)
```

```json
{
  "grade": "SENIOR",
  "label": "시니어",
  "completedProjectCount": 10,
  "ratingAverage": 4.2,
  "nextGrade": "MASTER",
  "nextGradeGuide": "별점 조건은 충족했습니다. 완료 프로젝트가 10건 더 필요합니다(현재 10건).",
  "checkedGuide": "매월 1일 자동 산정"
}
```

목표치(4.0점 / 20건)는 기준표 API에서 가져오세요.

> ⚠️ **와이어프레임 오류**: 프리랜서 화면의 등급 카드가 `골드 → 다이아` 로 그려져 있는데
> 프리랜서 등급은 **주니어 / 시니어 / 마스터** 입니다. `시니어 → 마스터` 로 고쳐주세요.
> (클라이언트가 실버 / 골드 / 다이아입니다)

> ⚠️ **지금은 `completedProjectCount` 가 0으로 나옵니다.** 성공보수 결제까지 끝난 계약이
> 아직 없어서입니다. 프로그레스바 0%는 버그가 아닙니다.

### 2-4. 이력서 / 포트폴리오

```
GET /api/v1/freelancers/me/resume          조회 (조건 + 이력서 한 번에)
PUT /api/v1/freelancers/me/resume          "변경사항 저장"
PUT /api/v1/freelancers/me/resume/draft    "임시 저장"
GET /api/v1/freelancers/me/resume/draft    임시저장 불러오기
```

**조회 응답**

```json
{
  "status": "COMPLETED",
  "lastModifiedAt": "2026-07-15T10:00:00",
  "condition": { },
  "resume": { },
  "notice": "수정한 이력서는 새로운 추천부터 반영됩니다. ..."
}
```

`notice` 는 화면 상단 파란 안내 박스 문구입니다. 서버가 내려주니 하드코딩하지 마세요.

#### (A) 기본 희망 조건 — **별도 API**

```
GET /api/v1/freelancers/me/condition
PUT /api/v1/freelancers/me/condition
```

```json
{
  "jobCategory": "DEVELOPMENT",
  "jobRole": "FRONTEND_DEVELOPER",
  "affiliation": "재직중",
  "workStyle": "ANY",
  "workForm": "FULL_TIME",
  "payUnit": "MONTHLY",
  "payAmount": 5000000,
  "minAcceptAmount": 4000000,
  "availableFrom": null,
  "startNegotiable": true,
  "periodValue": 6,
  "periodUnit": "MONTH",
  "hasFreelanceExperience": false,
  "careerYears": 1,
  "skills": [
    { "skillCode": "REACT", "skillLevel": "ADVANCED" },
    { "skillCode": "NEXTJS", "skillLevel": "INTERMEDIATE" }
  ]
}
```

| 화면 | 필드 | 값 |
|---|---|---|
| 근무 방식 | `workStyle` | `REMOTE`(재택) / `ONSITE`(상주) / `ANY`(모두 가능) |
| 근무 형태 | `workForm` | `FULL_TIME` / `PART_TIME` / `ANY` |
| 희망 급여 단위 | `payUnit` | `HOURLY`(시급) / `DAILY`(일급) / `MONTHLY`(월급) |
| 희망 급여 | `payAmount` | **원 단위, 만원 배수, 최소 10000** |
| 예상 기간 | `periodValue` + `periodUnit` | `MONTH`(개월) / `WEEK`(주) |
| 협의 가능 체크 | `startNegotiable` | 체크 시 `availableFrom` 은 null 가능 |
| 스킬 숙련도 | `skillLevel` | `BEGINNER`(초급) / `INTERMEDIATE`(중급) / `ADVANCED`(고급) |

- **스킬은 최소 1개** 필수. 숙련도 미선택 상태로 보내면 400
- 저장은 **전체 교체**입니다. 스킬 하나 지워도 전체 목록을 다시 보내세요

#### (B) 이력서 본문

학력 / 경력 / 자격증 / 자기소개 / 포트폴리오 / 링크가 **전부 이 요청 하나**에 들어갑니다.
`+ 학력 추가` `삭제` 버튼은 프론트 상태만 바꾸고, 실제 저장은 "변경사항 저장" 한 번입니다.

```json
PUT /api/v1/freelancers/me/resume
{
  "profileFileId": 3,
  "contactPhone": "010-1234-5678",
  "contactEmail": "kimgaebal@dev.kr",

  "zipCode": "06234",
  "address": "서울특별시 강남구 테헤란로 123",
  "addressDetail": "5층",

  "educations": [{
    "startDate": "2010-03-01",
    "endDate": "2014-02-28",
    "schoolName": "서울대학교",
    "major": "컴퓨터공학",
    "graduationStatus": "GRADUATED",
    "campusType": "MAIN"
  }],

  "careers": [{
    "startDate": "2014-03-01",
    "endDate": "2020-12-31",
    "companyName": "(주)테크스타트업",
    "departmentRank": "개발팀 선임",
    "jobDescription": "..."
  }],

  "certificates": [{
    "acquiredDate": "2020-05-01",
    "name": "정보처리기사",
    "issuer": "한국산업인력공단",
    "score": null,
    "note": null
  }],

  "selfIntroduction": "사용자 경험을 중요하게 생각하는 프론트엔드 개발자입니다.",
  "portfolioFileId": 12,
  "links": [{ "url": "https://github.com/kimfree" }],

  "agreements": {
    "profileCollectionAgreed": true,
    "profileProvisionAgreed": true,
    "aiAnalysisAgreed": true,
    "careerPortfolioUsageAgreed": true
  }
}
```

**주소** — 주소찾기 결과를 3필드로 나눠 보내세요.

| 화면 | 필드 |
|---|---|
| 우편번호 | `zipCode` |
| 도로명/지번 주소 | `address` |
| 상세주소 (사용자 입력) | `addressDetail` |

**학력** (`educations`)

| 화면 | 필드 | 비고 |
|---|---|---|
| 기간 `2010.03~2014.02` | `startDate` + `endDate` | **두 필드로 분리.** 재학중이면 `endDate` 생략 |
| 학교명 | `schoolName` | 필수, 100자 |
| 전공 | `major` | 선택, 100자 |
| 졸업구분 | `graduationStatus` | `GRADUATED`(졸업) / `EXPECTED`(졸업예정) / `ATTENDING`(재학중) / `LEAVE`(휴학) / `DROPPED`(중퇴) |
| 본교/분교 | `campusType` | `MAIN`(본교) / `BRANCH`(분교) |

**경력** (`careers`)

| 화면 | 필드 | 비고 |
|---|---|---|
| 기간 | `startDate` + `endDate` | 재직중이면 `endDate` 생략 |
| 회사명 | `companyName` | 필수, 100자 |
| 부서 + 직급 | `departmentRank` | ⚠️ **서버는 한 필드** — 아래 참고 |
| 담당업무 | `jobDescription` | 선택, 2,000자 |

> ⚠️ 화면은 `부서` / `직급` 입력칸이 따로인데 서버는 `departmentRank` 하나입니다.
> 당장은 `"개발팀 선임"` 처럼 합쳐 보내면 되지만, **수정 화면에서 두 칸으로 되돌려 채우려면
> 서버를 두 필드로 쪼개야 합니다.** 필요하면 알려주세요 — 바로 나눠드립니다.

**자격증 및 어학** (`certificates`) — 화면과 1:1 일치

`acquiredDate`(취득일) / `name`(자격증명) / `issuer`(발급기관) / `score`(점수) / `note`(비고)

**자기소개** — `selfIntroduction`, **최대 1,500자**

**포트폴리오** — `portfolioFileId` (5-1 업로드로 먼저 받기, PDF 100MB)
"교체" = 새로 업로드 → 새 `fileId` 로 저장 → 옛 파일 `DELETE`

**약관** — 4개 **전부 `true`** 여야 저장됩니다. 하나라도 false면 400

#### (C) 임시 저장

```json
PUT /api/v1/freelancers/me/resume/draft
{ "payload": { ...화면 상태 전부 아무 형태로... } }
```

- `payload` 는 **자유 JSON**입니다. 서버가 내용을 검증하지 않고 그대로 보관합니다
- 필수값이 비어 있어도 저장됩니다. 작성 중간에 저장하라고 만든 겁니다
- 최대 100,000자
- `GET /me/resume/draft` 로 그대로 돌려받아 폼에 다시 채우면 됩니다
- **임시저장은 이력서로 취급되지 않습니다.** 매칭 대상이 되려면 "변경사항 저장"으로 정식 저장해야 합니다

### 2-5. 리뷰 관리

```
GET /api/v1/reviews/summary                     상단 "4.8 총 17건"
GET /api/v1/reviews/received?page=0&size=10     받은 리뷰 탭
GET /api/v1/reviews/written?page=0&size=10      작성한 리뷰 탭
```

```json
// summary
{ "averageScore": 4.8, "reviewCount": 17, "grade": "SENIOR" }

// received / written (PageResponse)
{
  "content": [{
    "reviewId": 900,
    "contractId": 600,
    "projectTitle": "B2B 주문 관리 서비스 리뉴얼",
    "reviewerName": "주식회사 오이랩",
    "reviewerRole": "CLIENT",
    "score": 5,
    "content": "일정 준수가 정확하고 결과물의 완성도가 높았습니다.",
    "createdAt": "2027-01-02T10:00:00"
  }],
  "page": 0, "size": 10, "totalElements": 17, "totalPages": 2
}
```

- **태그 기능은 없습니다.** 별점 + 텍스트만입니다. 화면의 `일정 준수` `완성도 높음` 칩은 목업이니 빼주세요
- "수정 및 삭제 불가" 는 정적 배지입니다. 수정/삭제 API가 없습니다
- "작성한 리뷰" 아래 `서비스 이용 후기` 블록은 같은 응답에 포함돼 옵니다

---

## 3. 클라이언트 마이페이지

사이드바 7개: 기본 정보 / 기업 정보 / 리뷰 관리 / 결제수단 / 결제 내역 / 등급 및 혜택 / 회원 탈퇴

### 3-0. 기본 정보 + 기업 정보 — **API 하나입니다**

```
GET   /api/v1/clients/me
PATCH /api/v1/clients/me
```

두 탭이 같은 리소스의 다른 뷰라서 하나로 충분합니다.
**마이페이지 진입 시 한 번 받아 두 탭이 나눠 쓰면 됩니다.** 탭 전환마다 재호출 불필요.

```json
{
  "accountId": 3,
  "logoUrl": "https://cdn.../company-logo/xxx.png",
  "companyName": "주식회사 오이랩",
  "businessNo": "1234567890",
  "businessField": "IT_CONTENTS_AI",
  "employeeCount": "SIZE_50_299",
  "email": "contact@oelab.co.kr",
  "name": "김민준",
  "phone": "01012345678",
  "address": "서울특별시 강남구 테헤란로 123 10층",
  "grade": "GOLD",
  "ratingAverage": 4.2,
  "reviewCount": 8,
  "withdrawable": true
}
```

| 필드 | 기본 정보 탭 | 기업 정보 탭 |
|---|---|---|
| `logoUrl` | ✅ 프로필 원 | ✅ |
| `companyName` | ✅ | ✅ |
| `name` (담당자) | ✅ | ✅ |
| `businessNo` | ✅ | ✅ |
| `businessField` | ✅ | ✅ |
| `employeeCount` | ✅ | ✅ |
| `email` | ✅ | ✅ |
| `phone` | ✅ | — |
| `address` | — | ✅ |
| `grade` | ✅ 배지 | — |

`logoUrl` 이 `null` 이면 지금처럼 이니셜 원(`오`)을 그리면 됩니다.

### 3-1. 기업 정보 수정

⚠️ **이메일 인증 선행 필수** (4-1 참고)

```json
PATCH /api/v1/clients/me
{
  "companyName": "주식회사 오이랩",
  "employeeCount": "SIZE_50_299",
  "address": "서울특별시 강남구 테헤란로 123 10층",
  "phone": "010-1234-5678",
  "logoFileId": 7
}
```

| 항목 | 수정 |
|---|---|
| 기업명 `companyName` | ✅ 필수 |
| 직원 수 `employeeCount` | ✅ 필수 |
| 회사 주소 `address` | ✅ |
| 휴대폰번호 `phone` | ✅ |
| 기업 로고 `logoFileId` | ✅ |
| 사업 분야 | ❌ |
| 사업자등록번호 / 업무 이메일 / 담당자명 | ❌ |

> ⚠️ **화면 수정 필요**: 기업 정보 수정 폼의 `사업 분야` 드롭다운이 활성으로 보이는데
> 서버가 받지 않습니다. **비활성(회색)으로 바꿔주세요.**

> ⚠️ **휴대폰번호 수정 진입점이 없습니다.** `phone` 은 수정 가능한데 기본 정보 탭에만
> 표시되고 편집 UI가 없습니다. 기업 정보 폼에 넣거나 기본 정보에 수정 버튼이 필요합니다.

**`logoFileId` 는 보내지 않으면 기존 로고를 유지합니다.** 회사 주소만 바꿔 저장할 때마다
로고가 날아가면 안 되기 때문입니다. 로고를 안 바꾸는 저장에서는 아예 빼고 보내세요.

**기업 로고 변경 흐름**

```
① POST /api/v1/files?purpose=COMPANY_LOGO   (multipart, 5MB, jpg/jpeg/png)
   → { "fileId": 7, ... }
② PATCH /api/v1/clients/me   { ..., "logoFileId": 7 }
③ GET /api/v1/clients/me     → "logoUrl": "https://cdn.../..."
```

### 3-2. 등급 및 혜택 / 다음 등급까지

프리랜서와 **완전히 같은 API·같은 응답 구조**입니다. 컴포넌트 재사용 가능.

```
GET /api/v1/grades/me
GET /api/v1/grades?role=CLIENT
```

클라이언트 등급은 **실버 / 골드 / 다이아** (`SILVER` / `GOLD` / `DIAMOND`)

### 3-3. 리뷰 관리

프리랜서와 동일합니다 (2-5 참고).

> **`받은 리뷰` 탭도 필요합니다.** 클라이언트도 프리랜서에게 평가받습니다.
> `GET /api/v1/reviews/received` 가 그대로 동작합니다.

---

## 4. 공통 기능

### 4-1. 이메일 인증 (정보 수정 · 비밀번호 변경)

```
POST /api/v1/auth/email-verifications          코드 발송
POST /api/v1/auth/email-verifications/confirm  코드 확인
```

```json
// 발송
{ "email": "contact@oelab.co.kr", "purpose": "PROFILE_UPDATE" }
→ { "expiresAt": "2026-08-11T16:35:00", "remainingSendCount": 4 }

// 확인
{ "email": "contact@oelab.co.kr", "purpose": "PROFILE_UPDATE", "code": "123456" }
```

**`purpose` 를 정확히 보내야 합니다.**

| 화면 | purpose |
|---|---|
| 내 정보 / 기업 정보 수정 | `PROFILE_UPDATE` |
| 비밀번호 변경 | `PASSWORD_CHANGE` |

두 값을 서버가 분리 검증합니다. 비밀번호용 코드로 정보 수정을 통과할 수 없습니다.
같은 화면에 두 기능이 나란히 있으니 섞이지 않게 주의해주세요.

**인증은 1회용입니다.** 인증 후 수정 API를 두 번 호출하면 두 번째는 `EMAIL_NOT_VERIFIED` 입니다.
저장 실패로 재시도할 때도 인증부터 다시 태워야 합니다.

- `expiresAt` → 남은 시간 타이머
- `remainingSendCount` → 재발송 버튼 비활성 판단

| 에러 | 의미 |
|---|---|
| `EMAIL_SEND_LIMIT_EXCEEDED` | 발송 횟수 초과 |
| `VERIFICATION_CODE_MISMATCH` | 코드 불일치 |
| `VERIFICATION_CODE_EXPIRED` | 만료 |
| `VERIFICATION_ATTEMPT_EXCEEDED` | 시도 횟수 초과 |
| `EMAIL_NOT_VERIFIED` | 인증 없이 수정 시도 |

### 4-2. 비밀번호 변경 (3단계 위저드)

| 단계 | API |
|---|---|
| ① 이메일 인증 | `POST /auth/email-verifications` → `/confirm` (`purpose=PASSWORD_CHANGE`) |
| ② 비밀번호 설정 | `PATCH /api/v1/auth/password` |
| ③ 완료 | 응답 성공 |

```json
PATCH /api/v1/auth/password
{ "newPassword": "...", "newPasswordConfirm": "..." }
```

- **현재 비밀번호는 받지 않습니다.** 이메일 인증이 그 역할입니다
- 8자 이상 / 영문·숫자·특수문자 각 1개 이상
- **변경 후 모든 세션이 끊깁니다.** ③에서 "확인" → 로그인 화면으로 보내주세요

| 에러 | 의미 |
|---|---|
| `INVALID_PASSWORD_FORMAT` | 형식 미달 |
| `PASSWORD_CONFIRM_MISMATCH` | 확인 불일치 |
| `SAME_AS_CURRENT_PASSWORD` | 기존과 동일 |
| `SOCIAL_ACCOUNT_NO_PASSWORD` | 소셜 가입자 (비번 없음) |

### 4-3. 결제수단

```
GET /api/v1/accounts/me/payment-methods
PUT /api/v1/accounts/me/payment-methods/card
PUT /api/v1/accounts/me/payment-methods/bank-account
```

**정책: 카드 1개 + 계좌 1개** 입니다. 등록/삭제가 아니라 **수정(PUT)** 만 있습니다.

> ⚠️ **화면 문구 수정 필요**: "최대 3개의 카드를 등록할 수 있습니다" → 삭제해주세요.

**조회 응답** (배열, 최대 2건)

```json
[
  {
    "paymentMethodId": 1,
    "methodType": "CARD",
    "displayName": "신한카드 · 1234",
    "cardBrand": "신한카드",
    "cardLast4": "1234",
    "cardHolder": "홍길동",
    "bankName": null,
    "accountLast4": null
  },
  {
    "paymentMethodId": 2,
    "methodType": "BANK_ACCOUNT",
    "displayName": "신한은행 ****4567",
    "bankName": "신한은행",
    "accountLast4": "4567",
    "cardBrand": null, "cardLast4": null, "cardHolder": null
  }
]
```

**서버는 전체 카드번호·계좌번호를 절대 내려주지 않습니다.** 뒤 4자리만 있습니다.

> ⚠️ **마스킹 표기 통일 필요**: 화면마다 `****-****-****-4567` / `***-********-****362` 로
> 제각각입니다. **`bankName` + `accountLast4`** 조합 하나로 통일해주세요.
> (`displayName` 을 그대로 쓰면 가장 간단합니다)

**수정 요청**

```json
// 카드
PUT /api/v1/accounts/me/payment-methods/card
{ "cardBrand": "신한카드", "cardNumber": "1234567891234567", "cardHolder": "홍길동" }

// 계좌
PUT /api/v1/accounts/me/payment-methods/bank-account
{ "bankCode": "SHINHAN", "accountNo": "1234567891234567", "accountHolder": "홍길동" }
```

### 4-4. 파일 업로드

```
POST   /api/v1/files          multipart/form-data
GET    /api/v1/files/{fileId}
DELETE /api/v1/files/{fileId}
```

`file` (파일) + `purpose` (쿼리 파라미터)

| purpose | 용도 | 제한 |
|---|---|---|
| `PROFILE_IMAGE` | 프리랜서 프로필 사진 | 5MB, jpg/jpeg/png |
| `COMPANY_LOGO` | 기업 로고 | 5MB, jpg/jpeg/png |
| `PORTFOLIO` | 포트폴리오 | 100MB, pdf |
| `INQUIRY_ATTACHMENT` | 1:1 문의 첨부 | 10MB, pdf/jpg/jpeg/png |

응답의 `fileId` 를 각 API의 `~FileId` 필드에 넣어 저장합니다.
**업로드만 하고 저장 API를 안 부르면 파일이 어디에도 연결되지 않습니다.**

### 4-5. 코드 목록 (드롭다운 채우기)

```
GET /api/v1/meta/job-categories    직군
GET /api/v1/meta/job-roles         직무
GET /api/v1/meta/skills            스킬 검색 목록
GET /api/v1/meta/work-conditions   근무 조건 묶음
```

`work-conditions` 는 한 번에 5종을 내려줍니다.

```json
{
  "workStyles":  [{ "code": "REMOTE", "label": "재택" }, ...],
  "workForms":   [...],
  "payUnits":    [...],
  "periodUnits": [...],
  "skillLevels": [{ "code": "BEGINNER", "label": "초급" }, ...]
}
```

**enum 라벨을 하드코딩하지 말고 이 API를 쓰세요.**

`BusinessField`(사업 분야 20종), `EmployeeCount`(직원 수 5구간),
`GraduationStatus`, `CampusType` 은 아직 코드 API가 없습니다.
필요하면 말씀해주세요 — 추가하겠습니다.

---

## 5. 리뷰 작성 (결제 완료 화면 → 리뷰 작성하기)

```
POST /api/v1/reviews    성공 시 201
```

```json
{
  "contractId": 600,
  "counterpart": { "score": 5, "content": "일정 준수가 좋았습니다." },
  "site":        { "score": 5, "content": "매칭이 빨라 좋았습니다." }
}
```

- `counterpart` = 상단 카드(상대 평가) **필수**
- `site` = 하단 카드(서비스 이용 후기) **필수**
- `score` 필수, 1~5 정수 / `content` 선택, 500자 이하

**보낼 값은 `contractId` 하나입니다.** 프로젝트와 평가 상대는 서버가 계약에서 가져옵니다.
(이전 안내의 `projectId` · `revieweeAccountId` 는 **제거됐습니다**)

**클라이언트 화면과 프리랜서 화면이 완전히 같은 API입니다.** "누가 누구를 평가하는지"도
서버가 계약 당사자를 보고 판단하므로 프론트가 구분할 필요 없습니다.

**응답**

```json
{ "reviewId": 900, "contractId": 600, "projectTitle": "...", "reviewerName": "...",
  "reviewerRole": "CLIENT", "score": 5, "content": "...", "createdAt": "..." }
```

**작성 가능 시점 — 중요**

성공보수 수수료까지 결제되어 **프로젝트가 종료된 뒤부터** 작성할 수 있습니다. (정책 P51)
아직이면 `409 RV_004` 입니다.

| 에러 | 의미 |
|---|---|
| `400 RV_002` | 별점/내용 형식 오류 |
| `404 CT_001` | 없는 계약 |
| `403 CT_002` | 내 계약이 아님 |
| `409 RV_001` | 이미 이 계약에 리뷰를 씀 |
| `409 RV_004` | 아직 대금 지급이 안 끝남 |

**한 번 작성하면 수정·삭제할 수 없습니다.** 제출 전 확인 문구를 넣어주세요.

### 작성 대기 목록

```
GET /api/v1/reviews/pending
```

```json
[{ "contractId": 12, "projectTitle": "페어링 웹 리뉴얼",
   "counterpartName": "이프리", "completedAt": "2026-08-01T10:00:00" }]
```

페이징 없이 배열입니다. `contractId` 를 그대로 작성 요청에 넣으면 됩니다.

> **프리랜서 진입 경로로 이 API가 필요합니다.**
> 클라이언트는 성공보수 결제 완료 화면에서 바로 `[리뷰 작성하기]` 로 넘기면 됩니다.
> 그런데 **프리랜서는 그 방식이 안 됩니다.** 프리랜서도 성공보수를 내지만, 프로젝트를
> 닫는 건 클라이언트 결제입니다. 프리랜서가 먼저 결제하면 그 시점엔 아직 프로젝트가
> 완료 대기라 리뷰가 열리지 않고, 클라이언트가 낼 때쯤엔 이미 결제 화면을 떠난 뒤입니다.
>
> → 프리랜서 리뷰 진입은 **마이페이지 / 내 계약** 쪽에 이 목록을 두고 들어가는 방식으로
> 부탁드립니다. 목록이 비면 섹션을 감추면 됩니다.

---

## 6. 회원 탈퇴 — ⚠️ 아직 동작하지 않습니다

```
DELETE /api/v1/accounts/me
```

엔드포인트는 있지만 **내부가 비어 있는 스텁**입니다. 호출하면 성공 응답만 오고
실제로 탈퇴되지 않습니다. 계정 도메인 담당 파트라 제가 채울 수 없습니다.

`withdrawable` 값으로 **버튼 활성/비활성**만 먼저 붙여두시면 됩니다.
(미납 정산이 있으면 `false`)

---

## 7. 프론트 작업 체크리스트

### 반드시 고쳐야 하는 것

- [ ] 리뷰 작성 요청에서 `projectId`, `revieweeAccountId` 제거
- [ ] 리뷰 작성 대기 응답 스키마 교체 (`PendingReviewResponse`)
- [ ] `RV_004`(대금 지급 전) 에러 처리 추가
- [ ] 결제수단 "최대 3개의 카드" 문구 삭제
- [ ] 계좌 마스킹 표기 `bankName + accountLast4` 로 통일
- [ ] 기업 정보 수정 폼의 `사업 분야` 드롭다운 비활성 처리
- [ ] 프리랜서 등급 카드 `골드 → 다이아` → `시니어 → 마스터`
- [ ] 리뷰 태그 칩(`일정 준수` 등) 제거 — 서버에 없음
- [ ] 급여 입력/표시를 만원 단위로 (API 전송은 원)
- [ ] 자기소개 카운터 1,500자 유지 (서버도 1,500)

### 추가해야 하는 것

- [ ] 클라이언트 리뷰 관리에 `받은 리뷰` 탭
- [ ] 클라이언트 휴대폰번호 수정 진입점
- [ ] 프리랜서 기본 정보 수정에 이메일 인증 단계 (지금 `[수정]` 버튼만 있음)
- [ ] 프리랜서 리뷰 작성 진입점 (작성 대기 목록)
- [ ] 기업 로고 업로드 UI (`COMPANY_LOGO`)

### 확인이 필요한 것

- [ ] **경력의 `부서`/`직급` 분리** — 서버는 현재 `departmentRank` 한 필드.
      수정 화면에서 두 칸으로 되돌려야 하면 서버를 쪼개겠습니다
- [ ] `BusinessField` / `EmployeeCount` 코드 API 필요 여부

---

## 8. 알려진 제약 (서버 쪽 대기 중)

| 항목 | 상태 | 영향 |
|---|---|---|
| `completedProjectCount` | 항상 0 | 등급 프로그레스바 0% |
| `GET /reviews/pending` | 항상 빈 배열 | 작성 대기 섹션 비어 있음 |
| 프리랜서 성공보수 정산 | 미구현 (정산 파트) | 프리랜서 결제 목록에 성공보수 안 나옴 |
| 회원 탈퇴 | 스텁 (계정 파트) | 호출해도 탈퇴 안 됨 |

앞의 둘은 **성공보수 결제까지 끝난 계약이 생기면 코드 수정 없이 자동으로 채워집니다.**

---

문의는 편하게 주세요.
