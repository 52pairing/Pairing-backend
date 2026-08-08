# API 계약

현재 구현된 엔드포인트입니다. 계약이 바뀌면 이 문서를 함께 갱신합니다. (AGENTS.md 규칙)

- 성공 응답: `ApiResponse<T>` = `{ timestamp, status, code, message, data }`
- 실패 응답: `ErrorResponse` = `{ timestamp, status, errorCode, message, traceId }`
- 목록 응답: `PageResponse<T>` = `{ content, page, size, totalElements, totalPages, first, last }`
- 인증: `accessToken` HttpOnly 쿠키 또는 `Authorization: Bearer {token}`
- 공개 경로: `/api/v1/auth/**`, `/api/v1/meta/**`, `/api/v1/terms/**`, `/api/v1/home/**`, `/api/v1/grades` (그 외는 인증 필요)
- 관리자 경로: `/api/v1/{domain}/admin/**` 은 `ROLE_ADMIN` 만 접근 가능
- 로그인 사용자 식별자는 서버가 토큰에서 꺼낸다. 요청에 `accountId` 를 넣지 않는다.

> **01~03(Auth/Meta/Terms)만 구현 완료**입니다.
> **04 이후는 컨트롤러 + DTO 스켈레톤**으로, 계약(경로·JSON)만 고정되어 있고 내부 로직은 담당자가 채웁니다.
> 스켈레톤 엔드포인트는 요청과 무관하게 고정 응답을 돌려줍니다.

---

## 01. Auth

### 이메일 인증

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/email-verifications` | X | 인증코드 발송. body `{email, purpose}` (purpose: SIGNUP/UNLOCK/PASSWORD_CHANGE/PROFILE_UPDATE) |
| POST | `/api/v1/auth/email-verifications/confirm` | X | 코드 확인. body `{email, purpose, code}` |

- 코드 유효 3분, 입력 시도 5회, 발송 1시간 15회.
- `purpose`: `SIGNUP` / `UNLOCK` / `PASSWORD_CHANGE` / `PROFILE_UPDATE`. 용도가 다르면 코드도 다르다.
- 발송 응답 data: `{expiresAt, remainingSendCount}` — 프론트 타이머와 재발송 안내에 사용.
- 확인 성공 후 30분 안에 가입을 제출해야 한다.

### 회원가입

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/signup/client` | X | 클라이언트(기업) 가입 |
| POST | `/api/v1/auth/signup/freelancer` | X | 프리랜서 일반 가입 |
| POST | `/api/v1/auth/signup/freelancer/social` | X | 프리랜서 소셜 가입(티켓 필요, 성공 시 로그인 쿠키 발급) |
| GET | `/api/v1/auth/exists/email?email=&role=` | X | 이메일 중복(역할별) |
| GET | `/api/v1/auth/exists/phone?phone=&role=` | X | 휴대폰 중복(역할별) |
| GET | `/api/v1/auth/exists/business-no?businessNo=` | X | 사업자등록번호 중복 |

공통 body 항목(세 경로 모두 필수):

- `card`: `{cardNumber, cardBrand}` — 수수료 결제용
- `bankAccount`: `{bankCode, accountNo, accountHolder}` — 용역비 수령용
- `agreements[]`: `{termsId, agreed}`

소셜 가입 body에는 email이 없다. 티켓에 담긴 공급자 이메일을 사용한다.
카드번호·계좌번호는 하이픈을 넣어도 되며 서버가 숫자만 남겨 AES로 암호화 저장한다. 조회 시에는 카드 끝 4자리만 나간다.
`bankCode`는 `GET /api/v1/meta/banks` 의 코드를 쓴다. 목록에 없는 코드는 `AC_006`.

**이메일·휴대폰은 역할별로 유니크하다.** 같은 사람이 클라이언트 계정과 프리랜서 계정을 각각 가질 수 있고,
같은 역할 안에서는 소셜↔일반을 포함해 중복이 불가하다.

### 로그인 / 세션

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | X | body `{email, password, role}` (role 필수). 쿠키 2종 발급 |
| POST | `/api/v1/auth/refresh` | 쿠키 | Access 재발급 + Refresh 회전 |
| POST | `/api/v1/auth/logout` | 쿠키 | Redis 토큰·세션 삭제 + 쿠키 만료 |
| GET | `/api/v1/auth/me` | O | 현재 로그인 사용자 |

- Access 30분 / Refresh 7일.
- 중복 로그인 불가. 새 로그인이 이전 세션을 끊고, 이전 기기는 다음 요청에서 `401 GLOBAL_011`을 받는다.
- 로그인 응답 data의 `tempPassword=true`면 비밀번호 변경 화면으로 보내야 한다.

### 소셜 로그인 (프리랜서 전용)

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/auth/social/{provider}/authorize?returnUrl=` | X | `{authorizeUrl, state}` |
| POST | `/api/v1/auth/social/{provider}/callback` | X | body `{code, state}` |

`{provider}`는 `kakao` / `google`.
콜백 응답 data: `status`가 `LOGIN`이면 쿠키 발급 완료, `SIGNUP_REQUIRED`면 `{signUpTicket, email, name}`으로 추가 정보 입력 화면으로 이동.

### 계정 복구

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/find-email` | X | body `{name, phone}` → `accounts[{role, maskedEmail}]` |
| POST | `/api/v1/auth/password/reset-requests` | X | body `{email, role, name, phone}` → 3분 링크 메일 |
| POST | `/api/v1/auth/password/reset-confirm` | X | body `{token}` → 임시 비밀번호 메일 |
| PATCH | `/api/v1/auth/password` | O | body `{newPassword, newPasswordConfirm}` (마이페이지 변경, 인증코드 선행) |
| POST | `/api/v1/auth/unlock` | X | body `{email, role, code}` (UNLOCK 인증코드) |

- 아이디 찾기는 두 역할로 가입했다면 두 건을 반환한다. 사용자가 어느 탭으로 로그인할지 고를 수 있어야 한다.
- `reset-requests`는 계정 열거 방지를 위해 일치하지 않아도 200을 반환한다.
- 비밀번호 변경 후 모든 세션이 끊기므로 재로그인이 필요하다.
- **로그인 전 "비밀번호 찾기"와 로그인 후 "비밀번호 변경"은 방식이 다르다.**
  찾기는 메일 링크 → 임시 비밀번호 발급, 변경은 메일 인증코드 → 사용자가 새 비밀번호 직접 입력.
  변경은 현재 비밀번호를 받지 않는다.
- `PATCH /auth/password` 는 두 화면이 함께 쓰며 인증 요구만 다르다.
  마이페이지 변경은 `purpose=PASSWORD_CHANGE` 인증을 먼저 통과해야 하고(미인증 시 `AU_006`),
  **임시 비밀번호로 로그인한 직후에는 인증코드 없이** 바로 호출한다. 서버가 `tempPassword` 상태를 보고 가른다.

---

## 02. Meta

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/meta/business-fields` | X | 사업 분야 코드 목록 |
| GET | `/api/v1/meta/employee-counts` | X | 직원수 구간 코드 목록 |
| GET | `/api/v1/meta/banks` | X | 은행 코드 목록(금융결제원 기관코드) |
| GET | `/api/v1/meta/job-categories` | X | 직무 대분류 6종 |
| GET | `/api/v1/meta/job-roles?category=` | X | 직무 26종. `category` 생략 시 전체 |
| GET | `/api/v1/meta/skills?category=` | X | 기술스택 63종 |
| GET | `/api/v1/meta/work-conditions` | X | 근무형태·근무방식·급여단위·기간단위·숙련도를 한 번에 |

`job-categories` / `job-roles` / `skills` 응답 항목은 `{code, label, parentCode}` 형태로 통일했다.
`work-conditions` 는 `{workStyles, workForms, payUnits, periodUnits, skillLevels}` 로 묶여 나온다.

## 03. Terms

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/terms?role=CLIENT` | X | 가입 동의 항목 3개 (코드별 최신 버전) |
| GET | `/api/v1/terms/documents?role=CLIENT` | X | 약관 전문 + 개인정보 처리방침 (푸터 링크용) |

가입 화면 동의 항목은 셋으로 고정한다.

| code | 제목 | 필수 | 대상 | 근거 |
| --- | --- | --- | --- | --- |
| `SERVICE` | 서비스 이용약관 동의 | 필수 | 역할별로 내용이 다름 | 계약 |
| `PRIVACY_CONSENT` | 개인정보 수집 및 이용 동의 | 필수 | 공통 | 개인정보 보호법 §15①1 |
| `MARKETING` | 마케팅 정보 수신 동의 | **선택** | 공통 | 보호법 §15①1 + 정보통신망법 §50 |

`PRIVACY_POLICY`(개인정보 처리방침)는 **동의 대상이 아니다.** 보호법 §30상 수립·공개 의무라
`/documents` 에만 나오고 `GET /terms` 에는 포함되지 않는다. 가입 화면에 체크박스로 넣으면 안 된다.

응답의 `type` 이 `AGREEMENT` 인 항목만 `agreements[]` 에 넣는다.

---

# 스켈레톤 도메인 (계약만 고정)

## 04. File

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/files?purpose=` | O | multipart `file` 업로드 → `{fileId, fileUrl, ...}` |
| GET | `/api/v1/files/{fileId}` | O | 파일 메타 조회 |
| DELETE | `/api/v1/files/{fileId}` | O | 업로더 본인만 삭제 |

- `purpose`: `PROFILE_IMAGE` / `COMPANY_LOGO` / `PORTFOLIO` / `PROJECT_FILE` / `SIGNATURE`.
- 용량·확장자 제한은 purpose 별로 다르다. 초과/불일치 시 400(`GLOBAL_008` / `FI_003`).
- 다른 도메인은 파일 자체가 아니라 **`fileId` 만 참조**한다. (예: `logoFileId`, `fileIds[]`, `signatureFileId`)
- 응답의 `fileUrl` 은 CDN 절대경로로 자동 변환된다.
- 삭제는 업로더 본인만 가능(`FI_002`), 조회는 다른 도메인 응답에도 쓰이므로 소유자 제한이 없다.
- 다른 도메인은 presentation DTO가 아니라 `FileQueryUseCase.findObjectKey(fileId)` (application 포트)로
  object key 를 가져와 자기 응답의 `~Url` 필드에 담는다.

## 05. Home (비로그인 메인)

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/home/summary` | X | 성사율·프로젝트수·협상수·프리랜서수·만족도·누적금액 |
| GET | `/api/v1/home/site-reviews?size=` | X | 공개+홍보 설정된 사이트 후기 |
| GET | `/api/v1/home/faqs` | X | FAQ 목록 |

## 06. Account

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/accounts/me/payment-methods` | O | 카드·계좌 목록(각 1건, `methodType` 으로 구분: CARD/BANK_ACCOUNT) |
| PUT | `/api/v1/accounts/me/payment-methods/card` | O | body `{cardBrand, cardNumber, cardHolder}` 카드 정보 수정 |
| PUT | `/api/v1/accounts/me/payment-methods/bank-account` | O | body `{bankCode, accountNo, accountHolder}` 계좌 정보 수정 |
| DELETE | `/api/v1/accounts/me` | O | body `{currentPassword, reason}` 회원 탈퇴 |
| GET | `/api/v1/accounts/admin/summary` | ADMIN | 회원 요약 카드(전체·정상·정지·탈퇴·역할별) |
| GET | `/api/v1/accounts/admin?role=&status=&signupType=&keyword=&page=&size=` | ADMIN | 회원 목록 |
| GET | `/api/v1/accounts/admin/{accountId}` | ADMIN | 회원 상세 |
| POST | `/api/v1/accounts/admin/{accountId}/suspension` | ADMIN | body `{reason, days}` 정지 |
| DELETE | `/api/v1/accounts/admin/{accountId}/suspension` | ADMIN | 정지 해제 |

- 카드(수수료 결제) 1개 + 계좌(용역비 수령) 1개, 가입 시 각각 하나씩 만들어진다. 마이페이지에서는 **기존 값을 수정만** 한다.
  신규 등록·삭제·기본 결제수단 지정 API는 없다(둘 다 필수 항목이라 삭제 개념이 없음).
- 카드번호·계좌번호는 하이픈을 넣어도 되며 서버가 숫자만 남겨 암호화 저장한다. 조회 응답에는 마스킹된 `displayName` 만 나간다.
- 탈퇴는 진행 중 프로젝트나 미납 요금이 있으면 거부된다. 30일 재가입 제한이 걸린다.
- 역할별 마이페이지(조회·수정)는 20/21 도메인에 있다.
- 관리자 회원 상세는 목록과 응답이 다르다(`AdminAccountDetailResponse`). 활동 현황 6지표와 프로젝트 이력을 함께 준다.

## 10. Project

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/projects/pre-review` | CLIENT | AI 사전 검수(등록 5단계). body `{positions[]}` |
| POST | `/api/v1/projects` | CLIENT | 프로젝트 등록 |
| GET | `/api/v1/projects/mine?tab=&page=&size=` | CLIENT | 내 프로젝트 목록 |
| GET | `/api/v1/projects/mine/tab-counts` | CLIENT | 탭별 건수 배지 |
| GET | `/api/v1/projects/{projectId}` | O | 상세 |
| PUT | `/api/v1/projects/{projectId}` | CLIENT | 수정(모집 단계까지만) |
| POST | `/api/v1/projects/{projectId}/cancellation` | CLIENT | 등록 취소 |
| GET | `/api/v1/projects/{projectId}/pre-review` | CLIENT | 등록 후 검수 결과 조회 |
| POST | `/api/v1/projects/{projectId}/recruit-close` | CLIENT | 모집 종료 |
| POST | `/api/v1/projects/{projectId}/completion` | CLIENT | 프로젝트 완료 처리 |
| POST | `/api/v1/projects/{projectId}/termination` | CLIENT | 프로젝트 중도 종료 |
| POST | `/api/v1/projects/{projectId}/recruit-extensions` | CLIENT | 모집 기간 연장 |
| GET | `/api/v1/projects/admin/status-counts` | ADMIN | 상태별 건수 (관리자 탭 배지) |
| GET | `/api/v1/projects/admin?status=&keyword=&page=&size=` | ADMIN | 전체 프로젝트 |
| GET | `/api/v1/projects/admin/{projectId}` | ADMIN | 관리자 상세 |

등록은 6단계 위저드다: 등록 안내 → 기본 정보 → 직군 모집 → 상세정보 → **AI 사전 검수** → 최종 확인.
검수 단계에서 `POST /pre-review` 를 호출하고, 마지막 단계에서 `POST /projects` 로 한 번에 등록한다.

등록 body 핵심: `{title, positions[], startDesiredDate, startNegotiable, periodValue, periodUnit, budgetAmount, workStyle, workForm, workLocation, currentSituation, mainTask, detailScope, extraNote, fileIds[], noticeAgreed}`
`positions[]` = `{jobCategory, jobRole, minCareerYears, headcount, skills[], preferredNote}`

- 급여는 포지션이 아니라 **프로젝트 단위 예산(`budgetAmount`)** 으로만 받는다.
- 우대사항(`preferredNote`)은 스킬 코드가 아니라 자유 텍스트다.
- `jobCategory` 는 `DEVELOPMENT` / `DESIGN` 두 가지다.

`pre-review` 응답: `{allMatchable, items[{jobRole, headcount, expectedCandidateCount, matchable, message, suggestions[]}]}`
후보가 부족해도 그대로 등록할 수 있다. 착수금 수수료를 결제해야 실제 추천과 매칭이 시작된다(결제는 15번 정산 API).

`tab`(목록 탭 → 상태 묶음):

| tab | 라벨 | 포함 status |
| --- | --- | --- |
| `REGISTERED` | 등록 완료 | REGISTERED |
| `MATCHING` | 매칭중 | RECRUITING, NEGOTIATING, CONTRACT_PENDING |
| `IN_PROGRESS` | 진행 중 | IN_PROGRESS |
| `COMPLETION_PENDING` | 완료 대기 | COMPLETION_PENDING |
| `CLOSED` | 종료 | CLOSED |
| `CANCELED` | 취소됨 | CANCELED |

상세 응답의 `freelancers[]` 가 "프리랜서 현황"(프로젝트 정보 탭)과 "진행 현황" 탭 목록을 같이 담당한다.
진행 현황 탭 상단의 **프로젝트 완료 처리 / 중도 종료**는 계약 단위(`/contracts/{id}/completion`)가 아니라 프로젝트 단위다.
`statusHistories[]` 는 관리자 상세("상태 이력" 표)에서만 채워진다.

관리자 목록은 탭이 **상태 하나**에 대응한다(전체/등록 완료/모집중/협상중/계약 대기/진행중/완료 대기/종료/취소됨).
클라이언트의 묶음 탭과 다르므로 `status-counts` 를 따로 쓴다.
`projectNo`, `clientName`, `matchedFreelancerName` 은 관리자 목록 전용이고 내 프로젝트 목록에서는 null 이다.

## 11. Matching

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/matchings/positions/{positionId}/candidates` | CLIENT | AI 추천 후보 목록 |
| POST | `/api/v1/matchings/positions/{positionId}/rerecommendations` | CLIENT | body `{type, quantity}` 재추천 |
| POST | `/api/v1/matchings/candidates/{candidateId}/rejection` | CLIENT | 추천 후보 거절 |
| POST | `/api/v1/matchings/requests` | CLIENT | body `{positionId, candidateIds[]}` 매칭 요청 |
| GET | `/api/v1/matchings/requests?projectId=&positionId=&status=&page=&size=` | CLIENT | 보낸 요청 |
| GET | `/api/v1/matchings/requests/received?tab=&page=&size=` | FREELANCER | 받은 요청 (프로젝트 제안) |
| GET | `/api/v1/matchings/requests/{requestId}` | O | 요청 상세 |
| POST | `/api/v1/matchings/requests/{requestId}/acceptance` | FREELANCER | 수락 → 협상 시작 |
| POST | `/api/v1/matchings/requests/{requestId}/rejection` | FREELANCER | body `{reason}` 거절 |

- 후보 카드는 `fitReasons[]`(태그 칩), `payUnit`/`payAmount`, `ratingAverage`, `skills[]` 로 그린다. 적합도 점수 숫자는 화면에 노출하지 않는다.
- 재추천 `type`: `FREE`(무료 1회) / `PAID`(유료, 후보 1명당 10,000원). 최초 추천은 `INITIAL`. 프로젝트당 총 6회.
- 추천 후보를 모두 거절하면 무료 재추천이 활성화된다. 거절한 후보는 다시 추천되지 않는다.
- 매칭 요청 응답 기한은 3일이고, 거절·만료된 프리랜서는 그 프로젝트에서 재선택할 수 없다.
- 프리랜서의 "프로젝트 제안" 목록 `tab`: `ALL`(전체) / `REVIEWING`(검토 중) / `NEGOTIATING`(협상 중) / `CLOSED`(종료됨).
- 제안 카드는 `companyName`, `companyProfile`, `skills[]`, `workLabel`, `periodLabel`,
  `expiresAt`(D-day 배지), 협상 중이면 `currentRound`/`maxRound`(최대 15)와 `newProposalCount` 로 그린다.
- `status`: `REQUEST_PENDING` / `REJECTED` / `ACCEPTED` / `NEGOTIATING` / `NEGOTIATION_FAILED` / `CONTRACT_PENDING` / `CONTRACTED` / `IN_PROGRESS` / `COMPLETION_PENDING` / `CLOSED` / `TERMINATED`

## 12. Negotiation (A2A)

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/negotiations/mine?status=&page=&size=` | O | 내 협상 목록 |
| GET | `/api/v1/negotiations/{negotiationId}` | O | 협상 상세(쟁점별 현재 상태) |
| GET | `/api/v1/negotiations/{negotiationId}/messages` | O | 내가 보는 대화(내 에이전트 ↔ 나) |
| POST | `/api/v1/negotiations/{negotiationId}/start` | O | body `{conditions[{conditionType, value}]}` 마지노선 설정 후 협상 시작 |
| POST | `/api/v1/negotiations/{negotiationId}/answers` | O | body `{roundNo, answers[{conditionType, value, accepted}]}` |
| POST | `/api/v1/negotiations/{negotiationId}/final-approval` | O | body `{approved}` 최종 승인/거절 |
| POST | `/api/v1/negotiations/{negotiationId}/give-up` | O | body `{reason}` 협상 포기 |
| GET | `/api/v1/negotiations/admin/summary` | ADMIN | AI Agent 관리 요약(세션 수·평균 라운드·평균 소요일) |
| GET | `/api/v1/negotiations/admin?status=&keyword=&page=&size=` | ADMIN | 협상 세션 목록 |
| GET | `/api/v1/negotiations/admin/{negotiationId}` | ADMIN | 협상 상세 (협상 로그 탭) |
| GET | `/api/v1/negotiations/admin/{negotiationId}/raw-logs` | ADMIN | AI 원본 로그 (원본 로그 탭) |
| GET | `/api/v1/negotiations/admin/{negotiationId}/messages` | ADMIN | 에이전트 간 전체 로그 |

- 협상 당사자는 상대 에이전트와 직접 대화하지 않는다. 각자 자기 에이전트와만 주고받는다.
- 흐름: 상대 AI 초기 제안 확인 → `start` 로 쟁점별 마지노선 설정 → 라운드마다 거절된 쟁점만 `answers` 로 재입력 → 전 쟁점 합의 시 `final-approval`.
- 마지노선은 상대에게 노출되지 않는다. 쟁점별 값 형태가 달라 문자열로 받고 서버가 `conditionType` 에 맞춰 해석한다.
- 관리자 상세 화면은 탭이 둘이다. **협상 로그**(라운드별 제안·응답 아코디언) / **원본 로그**(`ai_agent_log` 원문).
- `status`: `IN_PROGRESS` / `AGREED` / `FAILED`

## 13. Chat

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/chat-rooms` | O | 내 채팅방 목록 |
| GET | `/api/v1/chat-rooms/unread-count` | O | 헤더 메시지 배지 숫자 |
| GET | `/api/v1/chat-rooms/{chatRoomId}` | O | 채팅방 상세 |
| GET | `/api/v1/chat-rooms/{chatRoomId}/messages?page=&size=` | O | 메시지 목록(과거 방향 페이징) |
| POST | `/api/v1/chat-rooms/{chatRoomId}/messages` | O | body `{content}` 최대 500자 |
| POST | `/api/v1/chat-rooms/{chatRoomId}/read` | O | 읽음 처리 |
| POST | `/api/v1/chat-rooms/{chatRoomId}/leave` | O | 나가기 |

- 채팅방은 협상 성사 후 자동 생성된다. 임의로 만들 수 없어서 생성 API 가 없다.
- 실시간 수신은 STOMP(WebSocket)를 쓰고, 위 REST 는 이력 조회·전송 용도다.

## 14. Contract

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/contracts?status=&page=&size=` | O | 내 계약 목록 |
| GET | `/api/v1/contracts/{contractId}` | O | 계약서 상세 |
| GET | `/api/v1/contracts/{contractId}/pdf` | O | 계약서 PDF |
| POST | `/api/v1/contracts/{contractId}/signature` | O | body `{agreed}` 전자 서명 |
| POST | `/api/v1/contracts/{contractId}/rejection` | O | body `{reason}` 서명 거부 |
| POST | `/api/v1/contracts/{contractId}/completion` | O | 완료 확인 |
| POST | `/api/v1/contracts/{contractId}/termination` | O | body `{reason, workedAmount}` 중도 파기 |

- `status`: `DRAFT` / `SIGN_PENDING` / `SIGNED` / `COMPLETED` / `REJECTED` / `TERMINATED`
- 계약서는 협상 결과로 자동 생성되므로 생성 API 가 없다.
- 서명은 이미지 업로드 없이 **전자 서명 동의**로 처리한다. 화면은 확인 모달 하나뿐이다.
- `signDeadline` 까지 서명하지 않으면 계약이 자동 취소될 수 있다. 화면 상단 경고 배너에 쓴다.
- `clauses[]` 가 계약서 본문(제1조~)이다. 화면에 순서대로 나열한다.

## 15. Settlement

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/settlements/mine?phase=&status=&page=&size=` | O | 내 수수료 내역 |
| GET | `/api/v1/settlements/{settlementId}` | O | 정산 상세 |
| POST | `/api/v1/settlements/{settlementId}/payment` | O | body `{paymentMethodId}` 수수료 결제 |
| GET | `/api/v1/settlements/penalties/mine` | O | 내 위약금 |
| POST | `/api/v1/settlements/penalties/{penaltyId}/payment` | O | body `{paymentMethodId}` 위약금 납부 |
| GET | `/api/v1/settlements/admin/summary` | ADMIN | 총수익·당월수익·예정·미납·위약금 집계 |
| GET | `/api/v1/settlements/admin?settlementNo=&payerRole=&phase=&status=&page=&size=` | ADMIN | 전체 정산 |

- 플랫폼이 다루는 돈은 **수수료와 위약금뿐**이다. 용역비 자체는 플랫폼을 거치지 않는다.
- 결제 모달에서 고른 `paymentMethodId` 를 함께 보낸다. 06번 결제수단 목록의 값이다.
- 결제 완료 화면과 관리자 상세가 `paymentMethodLabel`, `approvalNo`, `failReason`, `overdueReason`,
  `statusHistories[]` 를 쓴다. 상태 이력은 관리자 상세에서만 채워진다.
- 요율(SILVER·GOLD 기준): 착수금 1억 미만 클라이언트 3% · 프리랜서 4%, 1억 이상 클라이언트 2%.
  성공보수 1억 미만 클라이언트 7% · 프리랜서 6%, 1억 이상 클라이언트 6%. DIAMOND 는 각 1% 인하.
- `phase`: `DEPOSIT`(착수금) / `SUCCESS_FEE`(성공보수), `status`: `PENDING` / `PAID` / `OVERDUE` / `FAILED`

## 16. Review

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/reviews` | O | body `{contractId, counterpart{...}, site{...}}` |
| GET | `/api/v1/reviews/received?page=&size=` | O | 받은 리뷰 |
| GET | `/api/v1/reviews/written?page=&size=` | O | 작성한 리뷰 |
| GET | `/api/v1/reviews/summary` | O | 평균 별점·건수·등급 |
| GET | `/api/v1/reviews/pending` | O | 작성 대기 계약 |
| GET | `/api/v1/reviews/admin/site-reviews/summary` | ADMIN | 요약 카드 + 별점 분포 |
| GET | `/api/v1/reviews/admin/site-reviews?score=&writerRole=&visibility=&promoted=&page=&size=` | ADMIN | 사이트 후기 목록 |
| PUT | `/api/v1/reviews/admin/site-reviews/{siteReviewId}/visibility` | ADMIN | body `{visibility, promoted}` |

- 대금 지급이 끝난 계약만 작성 가능하고, 등록 후 수정·삭제할 수 없다.
- **상대 평가와 서비스 후기 모두 별점이 필수**다. 코멘트만 선택(각 500자)이다.
- 상대 평가와 사이트 후기를 한 화면에서 쓰므로 등록 API 가 하나다. 사이트 후기는 기본 `PRIVATE`.

## 17. Notification

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/notifications?unreadOnly=&page=&size=` | O | 알림 목록 |
| GET | `/api/v1/notifications/unread-count` | O | 미읽음 개수(뱃지) |
| PUT | `/api/v1/notifications/{notificationId}/read` | O | 단건 읽음 |
| PUT | `/api/v1/notifications/read-all` | O | 전체 읽음 |
| DELETE | `/api/v1/notifications/{notificationId}` | O | 단건 삭제 |
| DELETE | `/api/v1/notifications` | O | 전체 삭제 |

`type`: `MATCHING_*`(4) / `NEGOTIATION_*`(3) / `CONTRACT_*`(3) / `SETTLEMENT_DUE` / `INQUIRY_ANSWERED`.
응답의 `targetType` + `targetId` 로 이동할 화면을 정한다.

## 18. Support

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/support/chatbot/questions` | O | body `{sessionId, question}` (첫 질문은 sessionId 생략) |
| GET | `/api/v1/support/chatbot/quota` | O | 잔여 한도(하루 10회, 자정 초기화) |
| GET | `/api/v1/support/chatbot/suggested-questions` | O | 입력창 위 추천 질문 칩 |
| GET | `/api/v1/support/chatbot/sessions/{sessionId}/messages` | O | 대화 이력 |
| POST | `/api/v1/support/inquiries` | O | body `{category?, title, content, fileIds[]}` 1:1 문의 |
| GET | `/api/v1/support/inquiries/mine?status=&page=&size=` | O | 내 문의 |
| GET | `/api/v1/support/inquiries/{inquiryId}` | O | 문의 상세 |
| GET | `/api/v1/support/admin/inquiries?status=&page=&size=` | ADMIN | 문의 목록 |
| POST | `/api/v1/support/admin/inquiries/{inquiryId}/answer` | ADMIN | body `{answer}` |

챗봇과 1:1 문의는 **서로 독립된 창구**다. 챗봇을 거쳐야 문의할 수 있는 구조가 아니다.

- 작성 화면에 유형 선택 UI 가 없다. `category` 를 비워 보내면 서버가 내용으로 분류한다.
  값: `ACCOUNT` / `PROJECT` / `MATCHING` / `NEGOTIATION` / `CONTRACT` / `PAYMENT` / `REVIEW` / `ETC`
- 첨부파일은 04번 파일 API 로 먼저 올리고 `fileIds[]` 만 보낸다.
- 상세 화면은 `inquiryNo`(QNA-20260805-0012), `category`, `answererName` 을 쓴다.

## 20. Freelancer

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/freelancers/me` | FREELANCER | 마이페이지 |
| PATCH | `/api/v1/freelancers/me` | FREELANCER | body `{currentPassword, profileFileId, phone, address, aiMatchingAgreed}` |
| GET | `/api/v1/freelancers/me/condition` | FREELANCER | 내 조건 |
| PUT | `/api/v1/freelancers/me/condition` | FREELANCER | 조건 등록/수정 |
| GET | `/api/v1/freelancers/me/resume` | FREELANCER | **조건 + 이력서 통합 조회** (마이페이지 "내 이력서" 한 화면) |
| PUT | `/api/v1/freelancers/me/resume` | FREELANCER | 이력서 등록/수정 (포트폴리오 등록/삭제도 여기서 같이 처리) |
| GET | `/api/v1/freelancers/me/matching-settings` | FREELANCER | 매칭 설정 조회 |
| PUT | `/api/v1/freelancers/me/matching-settings` | FREELANCER | body `{aiMatchingAgreed, matchingPaused}` |

- 이름·생년월일·이메일은 수정할 수 없다. 비밀번호 변경은 `PATCH /api/v1/auth/password`.
- **조회는 통합, 저장은 분리**다. 마이페이지는 한 화면이라 `GET /me/resume` 하나로 `{condition, resume}` 를 함께 받고,
  등록 위저드는 단계별 저장이 필요해 `PUT /me/condition` 과 `PUT /me/resume` 를 따로 호출한다.
- 조건·이력서 저장 시 임베딩이 갱신된다. 필수 항목을 다 채우면 이력서가 `COMPLETED` 가 되고 매칭 대상이 된다.
- 하위 목록(학력/경력/자격증/링크/포트폴리오)은 **전체 교체** 방식이다. 포트폴리오는 별도 CRUD 없이
  `PUT /me/resume` 안에서 파일/링크로 함께 등록·삭제된다. 이력서 PDF 발급 API는 없다.
- `PUT /me/resume` 는 `agreements{profileCollectionAgreed, profileProvisionAgreed, aiAnalysisAgreed,
  careerPortfolioUsageAgreed}` 를 필수로 받는다(넷 다 true, 미동의 시 `FR_004`). 회원가입 약관(`terms`)과는
  별개이며 최초 등록 시 한 번만 받고 이후 수정은 이 값에 영향을 주지 않는다.

## 21. Client

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/clients/me` | CLIENT | 기업정보 조회(사업분야·사업자등록번호·회사명·직원수·담당자명·주소) |
| PATCH | `/api/v1/clients/me` | CLIENT | body `{companyName, employeeCount, address}` 기업정보 수정 |

사업자등록번호·사업 분야·담당자명(대표자명)·업무이메일은 수정할 수 없다.
전화번호·기업 로고는 계정 공통 화면("기본 정보" 탭, 06번 계정 도메인)에서 다루며 이 도메인 책임이 아니다.

## 07. Grade

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/grades?role=CLIENT` | X | 등급 기준표(승급 조건·유지 기준·혜택·수수료율) |
| GET | `/api/v1/grades/me` | O | 내 등급과 다음 등급까지 남은 조건 |

등급은 완료 실적과 평점으로 자동 산정된다. 변경 API는 없다.
클라이언트 `SILVER`/`GOLD`/`DIAMOND`, 프리랜서 `JUNIOR`/`SENIOR`/`MASTER`.
클라이언트 화면은 `feeRate`(숫자 표), 프리랜서 화면은 `feeNote`(문구)를 쓴다.
로그인 메인의 "등급별 혜택 안내" 표도 이 API를 그대로 쓴다.

## 30. Admin

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/dashboard` | ADMIN | 회원·프로젝트·협상·정산 요약 + 처리 대기 항목 |

관리자 화면은 대시보드 / 회원 관리 / 프로젝트 관리 / 거래·정산 관리 / 사이트 리뷰 관리 / AI Agent 관리 6개다.
대시보드만 여기 있고 나머지는 각 도메인의 `/admin/**` 을 쓴다.

---

## 에러 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| GLOBAL_006 | 401 | 토큰 없음 |
| GLOBAL_009 | 401 | 토큰 만료 → `/auth/refresh` |
| GLOBAL_010 | 401 | 토큰 위조 → 재로그인 |
| GLOBAL_011 | 401 | 다른 기기 로그인으로 세션 종료 → 모달 후 재로그인 |
| AU_001 | 401 | ID/PW 불일치 (사유를 구분하지 않음) |
| AU_002 | 423 | 5회 실패로 계정 잠금 → 이메일 인증 후 해제 |
| AU_003 | 429 | 이메일 발송 15회 초과 |
| AU_004 / AU_005 / AU_012 | 400 | 코드 불일치 / 만료 / 시도 초과 |
| AU_006 | 400 | 이메일 인증 미완료 |
| AU_007 / AU_008 / AU_009 | 409 | 이메일 / 휴대폰 / 사업자등록번호 중복 |
| AU_010 / AU_011 | 400 | 비밀번호 형식 / 확인 불일치 |
| AU_013 | 400 | 약관 동의 목록 누락 |
| AU_014 | 429 | IP 차단 (1시간 20회 → 2시간). message 에 재시도 가능 시각 포함 |
| AU_015 | 401 | 재발급 시 다른 기기 로그인 감지 |
| AU_016 | 401 | 리프레시 토큰 없음/무효 |
| AU_017 | 403 | 임시 비밀번호 상태 |
| AU_018 / AU_019 / AU_020 | 400/409/400 | 소셜 인증 실패 / 이미 연동됨 / 티켓 만료 |
| AU_021 / AU_022 | 403 | 재가입 제한 / 정지 계정 |
| AU_023 | 400 | 클라이언트 소셜 로그인 시도 |
| AU_024 | 404 | 아이디 찾기 결과 없음 |
| AU_025 | 400 | 만 18세 미만 |
| AU_026 | 500 | 메일 발송 실패 |
| AU_027 / AU_028 | 400 | 재설정 링크 무효 / 기존 비밀번호와 동일 |
| AU_029 | 400 | 소셜 전용 계정이라 비밀번호 변경 불가 |
| AC_001 ~ AC_005 | - | 계정 조회/상태 오류 |
| AC_006 | 400 | 지원하지 않는 은행 코드 |
| TM_002 / TM_003 | 400 | 필수 약관 미동의 / 알 수 없는 약관 포함 |
| FI_001 | 404 | 파일을 찾을 수 없음 |
| FI_002 | 403 | 업로더 본인이 아님(삭제 시도) |
| FI_003 | 400 | 허용 용량 초과 |
| FR_001 ~ FR_003 | - | 프리랜서 조건/이력서 조회·검증 오류 |
| FR_004 | 400 | 이력서 등록 필수 동의 4종 중 미동의 |
| FR_005 | 404 | 존재하지 않는 프리랜서(freelancer_profile.id) |
