# 회원가입 / 로그인 / 소셜로그인 설계

대상 요구사항: R13(회원가입), R14(로그인), R15(로그아웃), R16(역할 구분)
기준 스키마: `db/init/02-create-schema.sql` (v12)
기준 컨벤션: `docs/ai/backend-convention.md`, `docs/ai/security-guide.md`

이 설계는 코드로 적용을 마쳤다. 실제 구현과 다른 부분이 생기면 이 문서를 함께 고친다.
베이스 패키지는 리팩터링 이후 `com.pairing` 이다. (`com.pairing.template_server` 아님)
엔드포인트 요약은 `.ai/API.md` 에도 정리해 두었다.

---

## 1. 도메인 분해

기존 `account` / `social_account` / `client_profile` / `freelancer_profile` / `payment_method` / `terms` / `terms_agreement` / `email_verification` 8개 테이블을 3개 도메인 패키지로 나눈다.

| 도메인 | 소유 테이블 | 책임 |
| --- | --- | --- |
| `account` | account, social_account, client_profile, freelancer_profile, payment_method | 계정 애그리거트. 계정 생성·상태 전이(PENDING/ACTIVE/LOCKED/WITHDRAWN)·비밀번호 해시 보관·중복 판정·프로필/결제수단 보관 |
| `auth` | email_verification | 인증 흐름. 회원가입 오케스트레이션, 로그인/로그아웃/재발급, 이메일 인증, 아이디·비밀번호 찾기, 소셜 연동 |
| `terms` | terms, terms_agreement | 약관 버전 조회, 동의 이력 기록 |

도메인 간 호출 규칙(이 프로젝트의 헥사고날 규칙을 도메인 경계까지 확장):

- `auth.application` 은 `account.application.usecase`, `terms.application.usecase` **인바운드 포트 인터페이스만** 주입한다.
- 다른 도메인의 `infrastructure`, JPA 엔티티, Spring Data 리포지토리를 직접 참조하지 않는다.
- 도메인 모델(`account.domain.model.Account` 등)은 읽기 목적으로 도메인 간 전달 가능하다. JPA 엔티티는 불가.

### 왜 `auth` 와 `account` 를 나누는가

`account` 는 마이페이지·관리자 회원관리·탈퇴에서도 쓰인다. 인증 흐름(토큰, 인증코드, IP 차단)과 수명이 다르다.
반대로 `email_verification` 은 인증 흐름에서만 쓰이므로 `auth` 가 소유한다.

---

## 2. 패키지 구조

```text
com.pairing
├── auth
│   ├── presentation
│   │   ├── api
│   │   │   ├── AuthController                  로그인/로그아웃/재발급
│   │   │   ├── SignUpController                가입 3종 + 중복확인
│   │   │   ├── EmailVerificationController     인증코드 발송/확인
│   │   │   ├── SocialAuthController            소셜 인가 URL/콜백
│   │   │   ├── AccountRecoveryController       아이디 찾기/비밀번호 재설정/잠금 해제
│   │   │   ├── request/                        (record + @Schema + jakarta.validation)
│   │   │   └── response/
│   │   └── advice/AuthExceptionAdvice
│   ├── application
│   │   ├── usecase
│   │   │   ├── SignUpUseCase
│   │   │   ├── LoginUseCase
│   │   │   ├── TokenUseCase                    재발급/로그아웃
│   │   │   ├── EmailVerificationUseCase
│   │   │   ├── SocialAuthUseCase
│   │   │   └── AccountRecoveryUseCase
│   │   ├── service/                            위 UseCase 구현체
│   │   ├── command/                            ClientSignUpCommand, FreelancerSignUpCommand,
│   │   │                                       SocialSignUpCommand, LoginCommand, ...
│   │   ├── result/                             LoginResult, SocialAuthResult, ...
│   │   ├── port/                               아웃바운드 포트 (아래 3.2)
│   │   └── policy/
│   │       ├── PasswordPolicy                  형식 검증 + 임시 비밀번호 생성
│   │       ├── AgePolicy                        만 18세 판정
│   │       ├── EmailMaskingPolicy               아이디 찾기 마스킹
│   │       ├── VerificationCodeGenerator        6자리 코드
│   │       └── ContactPolicy                    이메일 소문자·전화 숫자만·SHA-256
│   ├── domain
│   │   ├── model/EmailVerification, VerificationPurpose
│   │   └── repository/EmailVerificationRepository
│   ├── infrastructure
│   │   ├── persistence/EmailVerificationJpaEntity, SpringDataEmailVerificationRepository,
│   │   │               EmailVerificationRepositoryAdapter
│   │   ├── mapper/EmailVerificationMapper
│   │   ├── redis/     TokenStoreRedisAdapter, EmailSendLimitRedisAdapter,
│   │   │              LoginAttemptRedisAdapter, PasswordResetTokenRedisAdapter,
│   │   │              SignUpTicketRedisAdapter, SessionRegistryRedisAdapter
│   │   ├── mail/      SmtpMailSenderAdapter
│   │   ├── oauth/     SocialOAuthClient, KakaoOAuthClient, GoogleOAuthClient,
│   │   │              SocialProfileProviderAdapter
│   │   └── security/  RedisTokenSessionValidator (global TokenSessionValidator 구현)
│   ├── exception/AuthErrorCode
│   └── settings/AuthSettings, OAuthSettings    @ConfigurationProperties("app.auth" / "app.oauth")
│
├── account
│   ├── presentation/api/MetaController         선택 목록(enum) 조회. 마이페이지 API는 후속
│   ├── application
│   │   ├── usecase/AccountCommandUseCase, AccountQueryUseCase
│   │   ├── service/
│   │   └── command/CreateClientAccountCommand, CreateFreelancerAccountCommand,
│   │               CreateSocialFreelancerAccountCommand, PaymentMethodCommand
│   ├── domain
│   │   ├── model/Account, SocialAccount, ClientProfile, FreelancerProfile, PaymentMethod,
│   │   │         Role, AccountStatus, SignupType, SocialProvider,
│   │   │         BusinessField, EmployeeCount, PaymentMethodType
│   │   └── repository/AccountRepository, SocialAccountRepository,
│   │                  ClientProfileRepository, FreelancerProfileRepository,
│   │                  PaymentMethodRepository
│   ├── infrastructure/persistence, mapper
│   └── exception/AccountErrorCode
│
└── terms
    ├── presentation/api/TermsController
    ├── application/usecase/TermsQueryUseCase, TermsAgreementCommandUseCase
    ├── domain/model/Terms, TermsAgreement, TermsCode
    ├── domain/repository/TermsRepository, TermsAgreementRepository
    ├── infrastructure/persistence, mapper
    └── exception/TermsErrorCode
```

`global` 재사용: `ApiResponse`, `BusinessException`, `GlobalErrorCode`, `GlobalJwtProvider`(토큰·쿠키), `PasswordEncoderConfig`(BCrypt), `RedisConfig`, `RedisKeys`, `ApiErrorCodeExample`. 새로 만들지 않는다.

---

## 3. 포트 정의

### 3.1 인바운드 (auth.application.usecase)

```java
public interface SignUpUseCase {
    Long signUpClient(ClientSignUpCommand command);
    Long signUpFreelancer(FreelancerSignUpCommand command);
    LoginResult signUpFreelancerBySocial(SocialSignUpCommand command); // 가입 직후 자동 로그인
    boolean isEmailDuplicated(String email);
    boolean isPhoneDuplicated(String phone);
    boolean isBusinessNoDuplicated(String businessNo);
}

public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}

public interface TokenUseCase {
    LoginResult reissue(String refreshToken);
    void logout(Long accountId);
}

public interface EmailVerificationUseCase {
    SendCodeResult send(SendCodeCommand command);
    void confirm(ConfirmCodeCommand command);
}

public interface SocialAuthUseCase {
    AuthorizeUrlResult authorizeUrl(SocialProvider provider, String returnUrl);
    SocialAuthResult callback(SocialCallbackCommand command); // LOGIN 또는 SIGNUP_REQUIRED
}

public interface AccountRecoveryUseCase {
    List<String> findMaskedEmails(FindEmailCommand command);
    void requestPasswordReset(PasswordResetRequestCommand command);
    void issueTempPassword(String resetToken);
    void changePassword(ChangePasswordCommand command);
    void unlock(UnlockCommand command);
}
```

### 3.2 아웃바운드 (auth.application.port)

| 포트 | 구현 어댑터 | 용도 |
| --- | --- | --- |
| `TokenStorePort` | `TokenStoreRedisAdapter` | refresh token 저장/조회/삭제 |
| `SessionRegistryPort` | `SessionRegistryRedisAdapter` | 단일 세션(sid) 등록·조회·삭제 |
| `EmailSendLimitPort` | `EmailSendLimitRedisAdapter` | 1시간 15회 제한, 남은 TTL 조회 |
| `LoginAttemptPort` | `LoginAttemptRedisAdapter` | IP 실패 카운트·차단 |
| `PasswordResetTokenPort` | `PasswordResetTokenRedisAdapter` | 재설정 링크 토큰(3분) |
| `SignUpTicketPort` | `SignUpTicketRedisAdapter` | 소셜 가입 티켓 |
| `MailSenderPort` | `SmtpMailSenderAdapter` | 인증코드·재설정 링크·임시 비밀번호 메일 |
| `SocialProfileProviderPort` | `SocialProfileProviderAdapter` | 인가 URL 생성, code→token 교환, 프로필 조회 |
| `OAuthStatePort` | `OAuthStateRedisAdapter` | 소셜 인가 state(CSRF) |
| `VerifiedMarkerPort` | `VerifiedMarkerRedisAdapter` | 이메일 인증 완료 마커 |
| `AccountSuspensionPort` | `AccountSuspensionRedisAdapter` | 정지 계정 판정(조회 전용) |
| `DataEncryptionPort` | `global/infrastructure/crypto/AesGcmDataEncryptionAdapter` | 카드번호·계좌번호 AES-256-GCM |

---

## 4. enum 매핑 (VARCHAR + `@Enumerated(EnumType.STRING)`)

| enum | 값 | 컬럼 |
| --- | --- | --- |
| `Role` | CLIENT, FREELANCER, ADMIN | account.role(20) |
| `SignupType` | EMAIL, SOCIAL | account.signup_type(20) |
| `AccountStatus` | PENDING, ACTIVE, LOCKED, WITHDRAWN | account.status(20) |
| `SocialProvider` | KAKAO, GOOGLE | social_account.provider(20) |
| `PaymentMethodType` | CARD, BANK_ACCOUNT | payment_method.method_type(20) |
| `VerificationPurpose` | SIGNUP, UNLOCK, PROFILE_UPDATE | email_verification.purpose(30) |
| `EmployeeCount` | SIZE_1_4, SIZE_5_9, SIZE_10_49, SIZE_50_299, SIZE_300_OVER | client_profile.employee_count(30) |
| `TermsCode` | SERVICE, PRIVACY_CONSENT, MARKETING, PRIVACY_POLICY | terms.code(50) |
| `TermsType` | AGREEMENT, POLICY | terms.terms_type(20) |

`BusinessField`(client_profile.business_field, VARCHAR(40)) 20종:

```text
IT_CONTENTS_AI(IT/컨텐츠/AI)      GAME(게임)                 SALES_DISTRIBUTION_LOGISTICS(판매/유통/물류)
MANUFACTURING(제조)               ADVANCED_SCIENCE(첨단/과학기술)  OTHER_SERVICE(기타 서비스업)
FINANCE(금융)                     EDUCATION(교육)             REAL_ESTATE(부동산)
ARTS_SPORTS_LEISURE(예술/스포츠/여가)  HEALTH_WELFARE(보건/복지)   CONSTRUCTION(건설)
LODGING_FOOD(숙박/요식)            AGRICULTURE_FISHERY(농림어업)  MARKETING(마케팅)
WATER_ENVIRONMENT(상수도/환경)      ELECTRICITY_GAS(전기/가스)   PUBLIC_ADMIN_DEFENSE(공공행정/국방)
MINING(광산업)                    MEDICAL_HEALTHCARE(의료/헬스케어)
```

각 enum은 한글 라벨을 필드로 갖고, 선택 목록은 `GET /api/v1/meta/*` 로 내려준다(이미 permitAll 경로).

---

## 5. API 계약

모든 응답은 `ApiResponse<T>` / 실패는 `ErrorResponse`. base path `/api/v1/auth` 는 이미 permitAll이다.

### 5.1 선행 조회

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/meta/business-fields` | X | 사업 분야 enum 목록 |
| GET | `/api/v1/meta/employee-counts` | X | 직원수 enum 목록 |
| GET | `/api/v1/terms?role=CLIENT` | X | 역할별 최신 약관(필수/선택 구분) |
| GET | `/api/v1/auth/exists/email?email=&role=` | X | 이메일 중복(역할별) |
| GET | `/api/v1/auth/exists/phone?phone=&role=` | X | 휴대폰 중복(역할별) |
| GET | `/api/v1/auth/exists/business-no?businessNo=` | X | 사업자등록번호 중복 |

중복확인 3종은 계정 열거에 쓰일 수 있으므로 IP 기준 분당 호출 제한을 건다(`LoginAttemptPort` 재사용).

### 5.2 이메일 인증

| 메서드 | 경로 | 본문 | 응답 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/email-verifications` | `{email, purpose}` | `{expiresAt, resendAvailableAt, remainingSendCount}` |
| POST | `/api/v1/auth/email-verifications/confirm` | `{email, purpose, code}` | `{verifiedUntil}` |

- 코드: 6자리 숫자, `expires_at = now + 3분`, `code_hash` 만 저장(BCrypt).
- 입력 시도 5회 초과 시 해당 코드 폐기(AU_012).
- 확인 성공 시 Redis `AUTH_SUCCESS:{purpose}:{email}` 30분 마커. 가입 제출 시 이 마커로 인증 완료를 판정한다.

### 5.3 회원가입

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/v1/auth/signup/client` | 클라이언트(이메일 전용) |
| POST | `/api/v1/auth/signup/freelancer` | 프리랜서 일반 |
| POST | `/api/v1/auth/signup/freelancer/social` | 프리랜서 소셜(티켓 필요) |

결제수단은 카드와 계좌를 각각 받는다. 수수료는 카드로 결제하고 용역비는 계좌로 받으므로 세 가입 경로 모두 둘 다 필수다.

`POST /api/v1/auth/signup/client` 요청:

```json
{
  "companyName": "주식회사 오이",
  "businessNo": "1234567890",
  "businessField": "IT_CONTENTS_AI",
  "employeeCount": "SIZE_10_49",
  "email": "owner@company.com",
  "name": "홍길동",
  "phone": "01012345678",
  "password": "Passw0rd!",
  "passwordConfirm": "Passw0rd!",
  "card":        { "cardNumber": "1234-5678-1234-5678", "cardBrand": "신한카드" },
  "bankAccount": { "bankCode": "088", "accountNo": "110-123-456789", "accountHolder": "홍길동" },
  "agreements": [ { "termsId": 1, "agreed": true }, { "termsId": 2, "agreed": true } ]
}
```

`POST /api/v1/auth/signup/freelancer` 요청: `name, phone, email, password, passwordConfirm, birthDate, card, bankAccount, agreements`.

`POST /api/v1/auth/signup/freelancer/social` 요청: `signUpTicket, name, phone, birthDate, card, bankAccount, agreements` (email은 티켓에서 꺼내며 요청 본문으로 받지 않는다 = 수정 불가 보장).

응답: 일반 가입은 `201 { accountId, role }`, 소셜 가입은 `201 { accountId, role }` + 로그인 쿠키 동시 발급.

### 5.4 로그인 / 세션

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | X | 이메일+비밀번호 |
| POST | `/api/v1/auth/refresh` | X(쿠키) | Access 재발급 + Refresh 회전 |
| POST | `/api/v1/auth/logout` | 쿠키 | Redis RT·SESSION 삭제, 쿠키 만료 |
| GET | `/api/v1/auth/me` | O | 현재 로그인 정보(role, name, isTempPassword) |

로그인 요청 `{email, password, role}` — 이메일이 역할별 유니크라 `role` 은 필수다.
`(email, role)` 로 조회하므로 탭을 잘못 고르면 "계정 없음"이 되어 `AU_001` 로 나간다.
응답 본문 `{accountId, role, name, isTempPassword}` + `Set-Cookie: accessToken, refreshToken`(HttpOnly).

### 5.5 소셜 로그인 (프리랜서 전용)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/v1/auth/social/{provider}/authorize?returnUrl=` | `{authorizeUrl}` 반환, state는 Redis 5분 |
| POST | `/api/v1/auth/social/{provider}/callback` | `{code, state}` → `LOGIN` 또는 `SIGNUP_REQUIRED` |

콜백 응답:

```json
{ "status": "LOGIN" }
{ "status": "SIGNUP_REQUIRED",
  "signupTicket": "...",
  "prefill": { "email": "user@gmail.com", "name": "홍길동" } }
```

`provider` 는 `kakao` / `google`. 클라이언트 역할로는 진입 자체를 막는다(AU_023).

### 5.6 계정 복구

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/find-email` | X | `{name, phone}` → `[{role, maskedEmail}]` |
| POST | `/api/v1/auth/password/reset-requests` | X | `{email, role, name, phone}` → 재설정 링크 메일(3분) |
| POST | `/api/v1/auth/password/reset-confirm` | X | `{token}` → 임시 비밀번호 발급·메일 |
| PATCH | `/api/v1/auth/password` | O | `{currentPassword, newPassword, newPasswordConfirm}` → 변경 후 전 세션 파기 |
| POST | `/api/v1/auth/unlock` | X | `{email, role, code}` → 잠금 해제(UNLOCK 인증코드 확인) |

마스킹 규칙: 로컬파트 앞 2글자 + `*` 반복, 도메인 전체 공개. `abcdefg@gmail.com` → `ab*****@gmail.com`.
로컬파트가 2글자 이하이면 첫 1글자만 공개한다.

---

## 6. 주요 흐름

### 6.1 클라이언트 이메일 회원가입

```text
FE                          AuthController            SignUpService                     저장소
 |-- GET /terms?role=CLIENT ------------------------------------------------------------>|
 |-- GET /auth/exists/* (email, phone, business-no) ------------------------------------->|
 |-- POST /auth/email-verifications {email, SIGNUP} --->| 발송 제한 확인(15/1h)
 |                                                      | 코드 생성 → email_verification INSERT
 |                                                      | 메일 발송(3분 안내)
 |-- POST /auth/email-verifications/confirm ----------->| 만료·시도횟수·해시 검증
 |                                                      | verified_at 기록 + AUTH_SUCCESS 30분
 |-- POST /auth/signup/client -------------------------->|
                                                        | 1. AUTH_SUCCESS 마커 확인 (없으면 AU_006)
                                                        | 2. 형식 검증(비밀번호/전화/사업자번호)
                                                        | 3. 중복 검증(email, phone, business_no)
                                                        | 4. 재가입 제한 확인(email_hash/phone_hash)
                                                        | 5. 필수 약관 전부 동의 확인
                                                        | 6. @Transactional 단일 트랜잭션:
                                                        |    account(ACTIVE, EMAIL, email_verified=true)
                                                        |    client_profile
                                                        |    payment_method x2 (CARD, BANK_ACCOUNT)
                                                        |    terms_agreement x N
                                                        | 7. AUTH_SUCCESS 마커 삭제
                                                        |<- 201 {accountId, role}
```

프리랜서 일반 가입은 3단계에서 사업자번호 검증이 빠지고, 6단계에서 `client_profile` 대신 `freelancer_profile`(birth_date)이 생성되며, 2단계에 만 18세 판정이 추가된다.

### 6.2 프리랜서 소셜 회원가입 / 로그인

```text
FE -> GET /auth/social/kakao/authorize     : state 발급(Redis 5분), authorizeUrl 반환
FE -> (공급자 로그인) -> FE 콜백 라우트에서 code, state 수신
FE -> POST /auth/social/kakao/callback {code, state}
        1. state 검증 후 즉시 삭제 (CSRF)
        2. code -> access token 교환 -> 프로필 조회(providerUid, email, emailVerified)
        3. social_account(provider, provider_uid) 존재?
             예 -> 계정 상태 확인 -> 토큰 발급 -> {status: LOGIN}
             아니오 -> 4
        4. 같은 email 의 account 존재? -> 예: 409 AU_007 (소셜↔일반 중복 불가)
        5. signupTicket 발급(Redis 30분: provider, providerUid, email, emailVerified)
           -> {status: SIGNUP_REQUIRED, signupTicket, prefill}
FE -> 추가 정보 입력(이름/전화/생년월일/카드/계좌) + 약관 동의
FE -> POST /auth/signup/freelancer/social {signupTicket, ...}
        6. 티켓 검증 -> 단일 트랜잭션:
             account(signup_type=SOCIAL, password_hash=NULL,
                     email_verified=공급자 인증값, status=ACTIVE)
             social_account
             freelancer_profile
             payment_method x2 (CARD, BANK_ACCOUNT)
             terms_agreement x N
        7. 티켓 삭제 -> 로그인 토큰 발급(쿠키) -> 201
```

소셜 가입에는 이메일 인증코드 절차가 없다(요구사항 R13). 이메일은 공급자 값 고정이며 서버가 요청 본문의 email을 받지 않는다.

### 6.3 로그인과 단일 세션

```text
POST /auth/login
 1. IP 차단 확인            -> 차단 중이면 429 AU_014 (해제 시각 안내)
 2. (email, role) 로 조회    -> 없으면 실패 카운트 증가 후 401 AU_001
 3. status 확인             -> WITHDRAWN 401 AU_001 / LOCKED 423 AU_002 / 정지 403 AU_022
 4. BCrypt matches?
      실패 -> login_fail_count++ , IP 카운트++ , 5회 도달 시 status=LOCKED, locked_at=now
              -> 401 AU_001 (5회째는 423 AU_002)
      성공 -> login_fail_count=0, last_login_at=now
 5. sessionId = UUID
    Redis SET SESSION:{accountId} = sessionId  (TTL 7일)
    Redis SET RT:{accountId}      = refreshToken (TTL 7일)   // 이전 값 덮어쓰기 = 이전 기기 무효화
 6. accessToken(sub=accountId, role, sid=sessionId, 1시간) + refreshToken(7일) 쿠키 발급
 7. isTempPassword=true 이면 응답에 표시 -> FE는 비밀번호 변경 화면으로 강제 이동
```

이전 기기의 강제 로그아웃 안내는 access token의 `sid` 를 Redis `SESSION:{accountId}` 와 대조해 처리한다.
불일치면 `401 AU_015`(다른 기기 로그인) 로 응답하고, FE가 모달을 띄운다.

검사 위치는 두 가지 중 하나를 고른다.

| 방식 | 검사 시점 | 지연 | 비용 |
| --- | --- | --- | --- |
| A(권장) | 매 요청. `global/security` 에 `TokenSessionValidator` 인터페이스를 두고 `auth.infrastructure.redis` 가 구현 | 즉시 | 요청당 Redis GET 1회 |
| B | `/auth/refresh` 시점만 | 최대 30분 | 없음 |

A를 권장한다. `global` 에는 인터페이스만 두므로 의존 방향(global -> 도메인)이 생기지 않는다.

### 6.4 토큰 재발급 / 로그아웃

```text
POST /auth/refresh
 1. refreshToken 쿠키 파싱(없거나 위조면 401 AU_016)
 2. Redis RT:{accountId} 와 값 일치 확인 -> 불일치면 401 AU_015 (다른 기기 로그인)
 3. 회전: 새 refreshToken 발급 후 Redis 덮어쓰기, 새 accessToken 발급(sid 유지)

POST /auth/logout
 1. Redis DEL RT:{accountId}, SESSION:{accountId}
 2. accessToken / refreshToken 쿠키 만료 (GlobalJwtProvider.deleteCookie)
```

Access 1시간·Refresh 7일은 `application.yaml` 에 설정되어 있다.

**액세스 토큰은 슬라이딩이다(2026-08-14).** `GlobalJwtAuthenticationFilter` 가 인증에 성공한 요청에서
남은 수명이 `jwt.access-token-renew-threshold`(기본 30분) 아래면 만료를 미룬 토큰을 새로 발급해
`Set-Cookie` 로 내려보낸다. 발급 시점 기준으로 딱 끊으면 작업 중이던 사용자가 로그인 화면으로 튕겨서다.

매 요청마다 발급하지 않는 이유는 두 가지다. 응답마다 `Set-Cookie` 가 붙고, 병렬 요청이 서로 다른
토큰을 덮어써 쿠키가 계속 튄다. 임계값을 두면 "그 시간 안에 요청이 한 번이라도 있으면 유지"가 된다.

**세션 ID(sid)는 물려준다.** 새로 만들면 진행 중이던 다른 요청이 단일 세션 검사에서 "다른 기기
로그인"으로 오인되어 끊긴다.

**리프레시 토큰 수명은 늘리지 않는다.** 그쪽이 절대 상한이라 계속 활동해도 7일 뒤에는 재로그인이
필요하다. 슬라이딩으로 무한정 늘리면 탈취된 세션도 영원히 살아 있게 된다.

### 6.5 계정 잠금과 비밀번호 재설정

```text
[잠금 해제]  로그인 5회 실패 -> status=LOCKED
  POST /auth/email-verifications {email, UNLOCK} -> 코드 발송
  POST /auth/unlock {email, code} -> 검증 성공 시 status=ACTIVE, login_fail_count=0

[비밀번호 찾기]
  POST /auth/password/reset-requests {email, name, phone}
     -> 3항목 일치 시 토큰 발급(Redis PW_RESET:{token} = accountId, TTL 3분)
     -> {FRONT_BASE_URL}/reset-password?token=... 링크 메일 발송
     -> 계정 열거 방지를 위해 불일치여도 200 으로 동일 응답
  POST /auth/password/reset-confirm {token}
     -> 임시 비밀번호 생성(정책 충족 난수) -> BCrypt 저장
     -> is_temp_password=true, password_updated_at=now
     -> Redis RT/SESSION 삭제(기존 세션 파기) -> 임시 비밀번호 메일 발송
  로그인 -> isTempPassword=true -> PATCH /auth/password 로 새 비밀번호 등록
     -> is_temp_password=false, 전 세션 파기 -> 재로그인
```

---

## 7. Redis 키 설계

`global/util/RedisKeys` 에 상수를 추가한다(기존 `RT:`, `AUTH_CODE:`, `AUTH_SUCCESS:` 재사용).

| 키 | 값 | TTL | 용도 |
| --- | --- | --- | --- |
| `RT:{accountId}` | refreshToken | 7일 | 재발급 대조. 덮어쓰기로 이전 기기 무효화 |
| `SESSION:{accountId}` | sessionId | 7일 | 단일 세션 판정(`sid` 클레임 대조) |
| `AUTH_SUCCESS:{purpose}:{email}` | "1" | 30분 | 이메일 인증 완료 마커 |
| `EMAIL_SEND:{email}` | 발송 횟수 | 1시간 | 15회 제한. 남은 TTL로 재시도 가능 시각 안내 |
| `LOGIN_FAIL_IP:{ip}` | 실패 횟수 | 1시간 | 20회 도달 시 차단 전환 |
| `LOGIN_BLOCK_IP:{ip}` | "1" | 2시간 | 차단. 남은 TTL로 해제 시각 안내 |
| `PW_RESET:{token}` | accountId | 3분 | 재설정 링크 |
| `SIGNUP_TICKET:{ticket}` | provider/uid/email/emailVerified | 30분 | 소셜 가입 티켓 |
| `OAUTH_STATE:{state}` | provider/returnUrl | 5분 | 소셜 CSRF 방지 |
| `SUSPEND:{accountId}` | 사유 | 정지 기간 | 스키마 주석대로 정지 상태는 Redis 관리 |

발송 횟수는 `INCR` + 최초 1회 `EXPIRE` 로 처리한다(고정 윈도우). 요구사항의 "현재시간 +1시간 이후 재시도" 안내와 정확히 맞는다.

---

## 8. 검증 규칙

| 항목 | 규칙 | 저장 형태 |
| --- | --- | --- |
| 이메일 | RFC 형식, 255자 이내. **역할별 유니크** | 소문자 정규화 |
| 비밀번호 | `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,20}$` | BCrypt(60자) |
| 비밀번호 확인 | 서버에서도 일치 검증(AU_011) | 저장 안 함 |
| 전화번호 | `^01[016789]\d{7,8}$` (하이픈 제거 후). **역할별 유니크** | 숫자만 |
| 사업자등록번호 | `^\d{10}$` + 국세청 체크섬 | CHAR(10) 숫자만 |
| 생년월일 | 만 18세 이상 (`birthDate <= today.minusYears(18)`) | DATE |
| 카드번호/계좌번호 | 하이픈 허용, 저장 전 숫자만 남김 | AES-256-GCM → BYTEA, 카드 끝 4자리만 평문 |
| 은행 코드 | `BankCode` enum(금융결제원 기관코드)에 있는 값만 | CHAR 아님, VARCHAR(10) 숫자 코드 |
| 약관 | `is_required=true` 인 최신 버전 전부 `agreed=true` | terms_agreement |

- 형식 검증은 request record의 `jakarta.validation`(1차) + `application/policy`(2차, 도메인 규칙)로 이중화한다. 소셜 가입은 request가 달라도 policy는 공유된다.
- 사업자등록번호 실제 등록 여부(국세청 API)는 요구사항상 "테스트 완료 후 추가 예정"이므로 `BusinessNoVerifierPort` 인터페이스만 두고 기본 구현은 형식·체크섬까지만 수행한다.

---

## 9. 에러 코드

`AuthErrorCode`(`AU_xxx`):

| 코드 | HTTP | 메시지 |
| --- | --- | --- |
| AU_001 | 401 | ID나 PW가 일치하지 않습니다. |
| AU_002 | 423 | 비밀번호를 5회 틀려 계정이 잠겼습니다. 이메일 인증 후 이용해 주세요. |
| AU_003 | 429 | 이메일 인증 요청 횟수를 초과했습니다. |
| AU_004 | 400 | 인증코드가 일치하지 않습니다. |
| AU_005 | 400 | 인증코드가 만료되었습니다. |
| AU_006 | 400 | 이메일 인증을 완료해 주세요. |
| AU_007 | 409 | 이미 가입된 이메일입니다. |
| AU_008 | 409 | 이미 가입된 휴대폰번호입니다. |
| AU_009 | 409 | 이미 등록된 사업자등록번호입니다. |
| AU_010 | 400 | 비밀번호는 대소문자, 숫자, 특수문자를 포함해 8자 이상 20자 이하여야 합니다. |
| AU_011 | 400 | 비밀번호가 일치하지 않습니다. |
| AU_012 | 400 | 인증 시도 횟수를 초과했습니다. 인증코드를 다시 요청해 주세요. |
| AU_013 | 400 | 필수 약관에 동의해야 합니다. |
| AU_014 | 429 | 로그인 시도가 많아 접근이 제한되었습니다. |
| AU_015 | 401 | 다른 기기에서 로그인되어 로그아웃되었습니다. |
| AU_016 | 401 | 로그인 정보가 만료되었습니다. 다시 로그인해 주세요. |
| AU_017 | 403 | 임시 비밀번호 상태입니다. 비밀번호를 변경해 주세요. |
| AU_018 | 400 | 소셜 인증에 실패했습니다. |
| AU_019 | 409 | 이미 연동된 소셜 계정입니다. |
| AU_020 | 400 | 가입 정보가 만료되었습니다. 처음부터 다시 진행해 주세요. |
| AU_021 | 403 | 탈퇴 후 30일이 지나야 재가입할 수 있습니다. |
| AU_022 | 403 | 이용이 정지된 계정입니다. |
| AU_023 | 400 | 클라이언트는 소셜 로그인을 사용할 수 없습니다. |
| AU_024 | 404 | 일치하는 회원 정보가 없습니다. |
| AU_025 | 400 | 만 18세 미만은 가입할 수 없습니다. |
| AU_026 | 500 | 메일 발송에 실패했습니다. |
| AU_027 | 400 | 비밀번호 재설정 링크가 만료되었거나 유효하지 않습니다. |
| AU_028 | 400 | 현재 비밀번호와 다른 비밀번호를 입력해 주세요. |

`AccountErrorCode`(`AC_xxx`): 계정 없음(AC_001), 프로필 없음(AC_002), 상태 전이 불가(AC_003), 필드 오류(AC_004), 역할별 소셜 불가(AC_005).
`TermsErrorCode`(`TM_xxx`): 약관 없음(TM_001), 필수 약관 미동의(TM_002), 알 수 없는 약관 포함(TM_003).

필수 약관 누락은 terms 도메인이 소유하므로 실제로는 **TM_002**가 나간다.
AU_013은 "동의 목록 자체가 비어 있음"에만 쓴다.

매 요청의 단일 세션 검사는 global 필터가 하므로 **GLOBAL_011**로 응답한다.
AU_015는 `/auth/refresh` 에서 저장된 리프레시 토큰이 덮어써진 것을 발견했을 때 쓴다.
둘 다 프론트에서는 같은 모달(다른 기기 로그인 안내)로 처리하면 된다.

429·423은 `CommonExceptionAdvice` 의 `BusinessException` 처리로 그대로 나간다. 별도 핸들러가 필요 없다.
Swagger는 컨트롤러 메서드마다 `@ApiErrorCodeExample(domain = AuthErrorCode.class, value = {...})` 로 붙인다.

---

## 10. 기존 코드 변경 (적용 완료)

1. **`GlobalJwtAuthenticationFilter.PUBLIC_AUTH_PATHS`** — 현재 `Set.contains(requestURI)` 정확 일치라서 `/api/v1/auth/signup/client` 처럼 하위 경로가 걸러지지 않는다. 브라우저에 만료된 `accessToken` 쿠키가 남아 있으면 가입/콜백 요청이 401로 막힌다. 접두사 매칭으로 바꾸거나 신규 경로를 모두 등록해야 한다. **접두사 매칭 권장.**
2. **`GlobalSecurityConfig`** — `/api/v1/terms` 를 permitAll에 추가한다. `/api/v1/auth/**` 는 이미 열려 있으므로, 인증이 필요한 `PATCH /api/v1/auth/password` 와 `GET /api/v1/auth/me` 에는 반드시 `@PreAuthorize("isAuthenticated()")` 를 붙인다.
3. **`GlobalJwtProvider.createAccessToken`** — `sid` 클레임을 추가하려면 시그니처 확장이 필요하다(`createAccessToken(subject, role, sessionId)`).
4. **`RedisKeys`** — 7장의 신규 키 상수 추가.
5. **`build.gradle`** — `spring-boot-starter-mail` 추가. 소셜은 `spring-boot-starter-oauth2-client` 대신 `RestClient` 직접 호출을 권장한다. SPA + 자체 JWT 쿠키 구조에서 Spring OAuth2 클라이언트의 세션·리다이렉트 모델은 오히려 우회 코드가 늘어난다.
6. **`example` 도메인 제거** — 실제 도메인 착수 시 `/api/v1/examples/**` permitAll 줄과 함께 삭제한다(`docs/ai/security-guide.md` 명시).
7. **`.ai/API.md`** — 새로 만들고 위 API 계약을 옮겼다.
8. **`db/init/03-seed-terms.sql`** — 약관 행이 없으면 가입이 불가능하므로(TM_003) 시드 파일을 추가했다. 내용은 자리표시자이며 실제 약관 전문으로 교체해야 한다.
9. **리팩터링 잔재 2건** — `RedisConfig.APPLICATION_BASE_PACKAGE` 와 `ExampleExceptionAdvice.basePackages` 가 옛 패키지(`com.example.template_server`)를 가리키고 있어 `com.pairing` 으로 맞췄다.

### 신규 환경변수 (값은 문서에 쓰지 않는다)

| 이름 | 용도 |
| --- | --- |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 인증 메일 발송 |
| `MAIL_FROM` | 발신자 주소 |
| `APP_FRONT_BASE_URL` | 비밀번호 재설정 링크 조립 |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` / `KAKAO_REDIRECT_URI` | 카카오 OAuth |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI` | 구글 OAuth |
| `DATA_ENCRYPTION_KEY` | 카드·계좌번호 AES 키(32바이트, Base64) |

---

## 11. 구현 현황

1~7은 적용을 마쳤고, 8(테스트 보강)이 남아 있다. 아래는 PR로 나눌 때의 권장 순서다.

1. `account` 도메인: enum, 도메인 모델, JPA 엔티티/매퍼/어댑터. `ddl-auto=validate` 통과 확인이 이 PR의 검증 기준.
2. `terms` 도메인 + 약관 조회 API + 시드 데이터.
3. `auth` 이메일 인증: `email_verification` 영속화, 발송 제한, 메일 어댑터.
4. 회원가입 3종 + 중복확인 API.
5. 로그인/로그아웃/재발급 + 단일 세션(`sid`) + IP 차단.
6. 소셜 로그인(카카오/구글) + 가입 티켓.
7. 아이디 찾기 / 비밀번호 재설정 / 잠금 해제.
8. 정책·도메인 단위 테스트 + 가입/로그인 통합 테스트.

### 안정화 과정에서 고친 것 (2026-08-05)

리뷰와 테스트에서 드러난 실제 결함이다. 같은 실수를 반복하지 않도록 남긴다.

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| 비밀번호를 몇 번을 틀려도 계정이 잠기지 않음 | 실패 횟수를 올린 뒤 같은 트랜잭션에서 예외를 던져 증가분이 롤백됨 | `applyLoginFailure` 를 `REQUIRES_NEW` 로 분리 |
| 인증코드를 무제한으로 시도할 수 있음 | 위와 같은 이유로 `attempt_count` 증가가 롤백됨 | `VerificationAttemptRecorder`(REQUIRES_NEW) 도입 |
| 컨트롤러·서비스 공통 로깅이 전혀 남지 않음 | 패키지 리팩터링 후 `ApiLoggingAop` 포인트컷이 옛 패키지를 가리킴 | `com.pairing.*..` 로 수정 |
| 쿼리 파라미터 검증 실패가 500 | `ConstraintViolationException` 핸들러 부재 | `CommonExceptionAdvice` 에 400 처리 추가 |
| 비로그인인데 403 | 익명 주체의 `@PreAuthorize` 거부를 권한 부족으로 처리 | 익명이면 401 `GLOBAL_006` 으로 구분 |
| 만료된 쿠키가 있으면 가입 화면의 약관·선택목록이 401 | 공개 조회 경로가 JWT 필터를 통과 | 필터 skip 목록에 `/api/v1/meta`, `/api/v1/terms` 추가 |
| Redis 장애 시 인증된 API 전체가 500 | 세션 검증 예외가 그대로 필터로 전파 | 검증 실패 시 fail-open(경고 로그) 후 통과 |
| SMTP 미설정이면 `/actuator/health` 가 503 | 메일 헬스체크가 전체 상태를 DOWN 으로 만듦 | `management.health.mail.enabled=false` |
| 소셜 콜백이 DB 커넥션을 잡고 외부 HTTP 호출 | 서비스에 클래스 트랜잭션 | `SocialAuthService` 트랜잭션 제거 |
| 재발급/내정보에서 `NumberFormatException` 500 가능 | 토큰 subject·principal 파싱 무방비 | 401 로 변환 (`parseAccountId`, `AuthenticatedAccount`) |

### 검증 범위

- `./gradlew clean test` 통과 (테스트 74개).
- 단위: 비밀번호·연령·마스킹·정규화 정책, `Account` 상태 전이, 로그인/가입/이메일 인증 서비스.
- 통합: `AuthFlowIntegrationTest` 가 가입 → 로그인 → `/me` 를 실제 요청으로 확인한다.
  역할별 중복 허용, 필수 약관 누락 시 계정까지 롤백되는지도 함께 본다.
- 엔티티-스키마 정합은 `db/init/02-create-schema.sql` 컬럼 목록과 대조해 확인했다.
  `ddl-auto=validate` 의 최종 확인은 실제 PostgreSQL 기동에서 이뤄진다.

---

## 12. 확인이 필요한 항목

설계상 한쪽으로 정하고 진행했지만, 답에 따라 결과가 달라지는 것들이다.

1. ~~이메일·휴대폰 유니크 범위~~ — **확정(2026-08-05)**: 한 사람이 클라이언트와 프리랜서로 각각 가입할 수 있다. 유니크를 `(email, role)`, `(phone, role)` 복합으로 바꿨고, 조회·중복 판정·재가입 제한이 모두 역할별로 동작한다. 같은 역할 안에서는 소셜↔일반 중복도 여전히 불가하다. 그 결과 로그인/비밀번호 재설정/잠금 해제 요청에 `role` 이 필수가 되었고, 아이디 찾기 응답은 역할을 함께 내려준다.
2. ~~연령 기준~~ — **확정(2026-08-05)**: 만 18세 계산 방식이 맞다. `AgePolicy` 가 생일까지 반영해 판정한다.
3. **잠금 후 복구 경로** — R14 본문은 "이메일 인증 후 임시비밀번호 발급", 제약사항은 "이메일 인증 후 로그인"이다. **제약사항 쪽(인증 후 잠금 해제, 임시 비밀번호는 비밀번호 찾기 전용)으로 설계했다.**
4. ~~결제수단 필수 개수~~ — **확정(2026-08-05)**: 가입에서 카드 1건 + 계좌 1건을 **둘 다 필수**로 받는다.
   요청 형태는 배열이 아니라 `card` / `bankAccount` 두 객체다. 한 항목에 카드와 계좌 필드를 섞어 보내
   한쪽이 조용히 누락되는 사고를 막기 위해서다. 은행은 `BankCode` enum으로 검증하고
   화면 목록은 `GET /api/v1/meta/banks` 가 내려준다. 변경(재등록)은 마이페이지 소관이다.
5. **`account.status = PENDING` 의 용도** — 현재 설계는 가입 완료 즉시 `ACTIVE`다. 사업자등록번호 진위확인 API가 붙으면 확인 전 상태로 `PENDING` 을 쓸 수 있는데, 지금 도입할지 확인이 필요하다.
6. **소셜 이메일이 기존 일반 계정과 같을 때** — 요구사항의 "소셜 ↔ 일반 이메일 중복 불가"에 따라 **자동 연동 없이 409로 차단**하도록 설계했다.
7. **관리자(ADMIN) 계정 생성 경로** — R16에 역할만 있고 생성 방법이 없다. 초기 시드 INSERT로 처리할지, 관리자 화면에서 생성할지 미정이다.
