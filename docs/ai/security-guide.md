# Security Guide

시크릿, 인증/인가, 토큰 처리, 파일 업로드, 개인정보 관련 변경에 사용하는 문서입니다.

## 시크릿 취급

- 실제 비밀번호, 토큰, 시크릿 키, 개인키, API 키, 인증정보 값을 코드나 문서에 쓰지 않습니다.
- 문서에는 환경변수 **이름과 용도**만 적습니다. 값은 적지 않습니다.
- `.env`, `application-secret.yml`, 개인키 파일, 로컬 인증정보 파일을 커밋하지 않습니다.
- 시크릿이 코드/로그/문서에 노출된 것을 발견하면 작업을 멈추고 어떻게 교체할지 물어봅니다.

## 환경변수

### 데이터베이스 / 캐시

- `DB_URL`: PostgreSQL JDBC URL (예: `jdbc:postgresql://localhost:5432/pairing`)
- `DB_USERNAME`: PostgreSQL 역할명
- `DB_PASSWORD`: PostgreSQL 비밀번호
- `REDIS_HOST`: Redis 호스트
- `REDIS_PORT`: Redis 포트
- `REDIS_SSL_ENABLED`: 전송 중 암호화 사용 여부

### 인증

- `JWT_SECRET_KEY`: JWT 서명 키. **필수입니다. 기본값이 없으므로 주입하지 않으면 기동에 실패합니다.** HS256이므로 32바이트 이상이어야 합니다. (생성: `openssl rand -base64 48`)
- `COOKIE_DOMAIN`: 토큰 쿠키 도메인. 비워두면 host-only 쿠키로 동작해 localhost에서도 저장됩니다.
- `COOKIE_SECURE`: https 배포 시 `true`. `true`일 때만 `SameSite=None`(크로스 도메인 전송)이 유효합니다.
- `CORS_ALLOWED_ORIGINS`: 허용 오리진 목록(쉼표 구분). credentials를 쓰므로 와일드카드 `*`는 사용할 수 없습니다.

### AWS S3

- `S3_BUCKET`: 버킷명. **필수입니다. 없으면 기동에 실패합니다.**
- `AWS_REGION`: 리전 (기본값 `ap-northeast-2`)
- `S3_CDN_URL`: 응답 URL 루트(CloudFront 도메인). 비우면 버킷·리전으로 자동 조합
- `AWS_ACCESS_KEY` / `AWS_SECRET_KEY`: 액세스 키. **비워두는 것을 권장합니다.**

액세스 키를 비우면 AWS 기본 자격증명 체인(환경변수 → `~/.aws/credentials` → EC2/ECS IAM 역할)을 사용합니다.
배포 환경에서는 키를 환경변수로 심지 말고 태스크·인스턴스 IAM 역할을 쓰십시오. 유출 경로가 줄고 교체가 자동으로 처리됩니다.
S3에 부여할 최소 권한은 `s3:PutObject`, `s3:DeleteObject`, `s3:ListBucket`입니다.

## 인증 / 인가

이 프로젝트는 stateless JWT를 사용합니다.

- 토큰은 `Authorization: Bearer {token}` 헤더 또는 `accessToken` 쿠키에서 읽습니다 (`GlobalJwtAuthenticationFilter`).
- 토큰 `subject`에 사용자 식별자를, `role` 클레임에 권한을 담습니다. 필터가 `ROLE_{role}` 권한으로 변환합니다.
- 경로 단위 규칙은 `GlobalSecurityConfig`에, 세부 권한은 각 컨트롤러의 `@PreAuthorize`에 둡니다.

### 기본값은 "닫힘"입니다

`GlobalSecurityConfig`의 마지막 규칙은 `anyRequest().authenticated()`입니다.

- **새로 만든 API는 기본적으로 인증이 필요합니다.** 공개해야 한다면 `permitAll()` 목록에 경로를 명시적으로 추가하십시오.
- 이 순서를 뒤집어 `permitAll()`을 기본값으로 만들지 마십시오. `@PreAuthorize`를 빠뜨린 API가 전체 공개되고, 그 사실을 아무도 모릅니다.
- `/api/v1/examples/**`는 데모용으로 열려 있습니다. 실제 프로젝트를 시작할 때 `example` 도메인과 함께 지웁니다.

### 인증 실패 응답 형식은 하나입니다

발생 위치가 달라도 응답 형태는 `ErrorResponse`로 통일되어 있습니다. 프론트엔드는 한 가지 파싱만 하면 됩니다.

| 상황 | 코드 | 처리 주체 |
| --- | --- | --- |
| 토큰 없음 | 401 `GLOBAL_006` | `CustomAuthenticationEntryPoint` |
| 토큰 만료 | 401 `GLOBAL_009` | `GlobalJwtAuthenticationFilter` |
| 토큰 위조/형식 오류 | 401 `GLOBAL_010` | `GlobalJwtAuthenticationFilter` |
| 권한 부족 (경로 규칙) | 403 `GLOBAL_005` | `CustomAccessDeniedHandler` |
| 권한 부족 (`@PreAuthorize`) | 403 `GLOBAL_005` | `CommonExceptionAdvice` |

만료(`GLOBAL_009`)와 위조(`GLOBAL_010`)를 구분하는 것이 중요합니다. 만료는 재발급으로 복구되지만 위조는 재로그인이 필요합니다.
필터에서 `parseClaims()` 대신 `validateToken()`(boolean)을 쓰면 이 구분이 사라지고 전부 `GLOBAL_006`으로 뭉개집니다.

시큐리티 필터체인에서 응답을 만들 때는 직접 JSON을 조립하지 말고 `ErrorResponseWriter`를 사용하십시오.
`TraceIdFilter`는 시큐리티 체인보다 먼저 실행되도록 `@Order(HIGHEST_PRECEDENCE)`가 지정되어 있어, 인증 실패 응답에도 로그와 대조 가능한 `traceId`가 실립니다. 이 순서를 바꾸면 traceId가 어긋납니다.

인증 동작을 바꾸기 전에:

1. `GlobalSecurityConfig`의 기존 경로 규칙을 확인합니다.
2. `GlobalJwtAuthenticationFilter`의 `PUBLIC_AUTH_PATHS`를 확인합니다. 로그인/재발급 경로는 낡은 쿠키 때문에 막히지 않도록 필터를 건너뜁니다.
3. `.ai/API.md`에서 영향받는 엔드포인트를 확인합니다.
4. 요청 헤더, 상태코드, 에러코드가 바뀌면 프론트엔드 영향을 문서화합니다.
5. `SecurityErrorResponseTest`를 실행해 응답 형식과 fail-closed 동작이 유지되는지 확인합니다.

## API 보안

- 요청 본문은 `jakarta.validation`으로 검증합니다.
- 내부 예외 상세를 응답에 노출하지 않습니다. `CommonExceptionAdvice`의 공통 처리를 사용합니다.
- 에러 응답에는 `traceId`가 함께 나가므로, 상세 원인은 로그에서 그 ID로 추적합니다.

## 파일 업로드 보안

- 업로드 시 파일 종류와 크기를 검증합니다. 크기 상한은 `spring.servlet.multipart.max-file-size`입니다.
- **원본 파일명을 저장 경로에 쓰지 않습니다.** 경로 조작(`../`) 위험이 있습니다. `S3StorageAdapter`가 UUID로 새 이름을 만듭니다.
- 종류 판별은 `FileTypeDetector`를 사용합니다. MIME 타입과 확장자는 클라이언트가 보낸 값이라 신뢰할 수 없습니다. 실행 파일 차단처럼 보안이 목적이면 파일 시그니처(magic number) 검사를 추가해야 합니다.
- 스토리지 기능은 `global`의 `FileStoragePort`를 재사용하고 새로 만들지 않습니다.
- 버킷을 공개(public-read)로 열기 전에 그 버킷에 비공개 데이터가 섞이지 않는지 확인합니다.
