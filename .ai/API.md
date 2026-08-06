# API 계약

현재 구현된 엔드포인트입니다. 계약이 바뀌면 이 문서를 함께 갱신합니다. (AGENTS.md 규칙)

- 성공 응답: `ApiResponse<T>` = `{ timestamp, status, code, message, data }`
- 실패 응답: `ErrorResponse` = `{ timestamp, status, errorCode, message, traceId }`
- 인증: `accessToken` HttpOnly 쿠키 또는 `Authorization: Bearer {token}`
- 공개 경로: `/api/v1/auth/**`, `/api/v1/meta/**`, `/api/v1/terms/**` (그 외는 인증 필요)

---

## 01. Auth

### 이메일 인증

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/email-verifications` | X | 인증코드 발송. body `{email, purpose}` (purpose: SIGNUP/UNLOCK/PROFILE_UPDATE) |
| POST | `/api/v1/auth/email-verifications/confirm` | X | 코드 확인. body `{email, purpose, code}` |

- 코드 유효 3분, 입력 시도 5회, 발송 1시간 15회.
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

공통 body 항목: `agreements[]`(`{termsId, agreed}`).
소셜 가입 body에는 email이 없다. 티켓에 담긴 공급자 이메일을 사용한다.

**가입에서 결제수단(카드/계좌)은 받지 않는다.** 로그인 후 마이페이지에서 등록·수정한다.

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
| PATCH | `/api/v1/auth/password` | O | body `{currentPassword, newPassword, newPasswordConfirm}` |
| POST | `/api/v1/auth/unlock` | X | body `{email, role, code}` (UNLOCK 인증코드) |

- 아이디 찾기는 두 역할로 가입했다면 두 건을 반환한다. 사용자가 어느 탭으로 로그인할지 고를 수 있어야 한다.
- `reset-requests`는 계정 열거 방지를 위해 일치하지 않아도 200을 반환한다.
- 비밀번호 변경 후 모든 세션이 끊기므로 재로그인이 필요하다.

---

## 02. Meta

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/meta/business-fields` | X | 사업 분야 코드 목록 |
| GET | `/api/v1/meta/employee-counts` | X | 직원수 구간 코드 목록 |

## 03. Terms

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/terms?role=CLIENT` | X | 역할별 최신 약관(코드별 최신 버전) |

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
| AU_014 | 429 | IP 차단 (1시간 20회 → 2시간) |
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
| AC_001 ~ AC_005 | - | 계정 조회/상태 오류 |
| TM_002 / TM_003 | 400 | 필수 약관 미동의 / 알 수 없는 약관 포함 |
