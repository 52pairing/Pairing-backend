# pairing

Spring Boot 3.5 + Java 17 기반 **헥사고날(포트-어댑터) 아키텍처 백엔드**입니다.

요구사항명세·정책 기준으로 **컨트롤러 계층(API 계약)이 확정된 상태**입니다.
엔드포인트 127개와 Swagger 문서가 모두 갖춰져 있고, 각 컨트롤러는 요청/응답 형태를 고정한 stub 응답을 반환합니다.
`// TODO: {Domain}UseCase 연결` 주석 위치에 application·domain·infrastructure 계층을 붙여나가면 됩니다.

전체 엔드포인트 맵과 정책 반영 지점은 [.ai/API.md](.ai/API.md)에 정리되어 있습니다.

> ⚠️ `POST /api/v1/auth/login`은 자격 증명을 검증하지 않고 토큰을 발급합니다.
> Swagger에서 인증이 필요한 API를 호출해보기 위한 임시 동작이므로 실제 인증 로직을 붙이기 전에 배포하면 안 됩니다.

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.5.14 |
| 영속성 | Spring Data JPA, PostgreSQL (테스트·로컬은 H2 PostgreSQL 호환 모드) |
| 인증 | Spring Security + JWT (jjwt) |
| 캐시 / 저장소 | Redis (Lettuce) |
| 파일 저장 | AWS S3 |
| 문서화 | springdoc-openapi (Swagger UI) |
| 매핑 | MapStruct |
| 기타 | Lombok, AOP, Actuator |

## 실행

### 1) 로컬 개발 — 인프라 없이 바로 실행

현재 컨트롤러는 모두 고정 응답(stub)이라 DB가 필요하지 않습니다. `local` 프로파일은 인메모리 H2를 쓰고
개발용 더미 설정을 포함하므로 **아무 준비 없이** 서버가 뜹니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 헬스체크: `http://localhost:8080/actuator/health`

Swagger에서 `POST /api/v1/auth/login`을 먼저 호출하면 토큰 쿠키가 세팅되어 나머지 API를 그대로 호출할 수 있습니다.

### 2) 실제 인프라로 실행

```bash
docker compose up -d
```

> 로컬에 PostgreSQL을 직접 설치해 쓰고 있으면 5432가 충돌합니다. 그 경우 Redis만 띄우세요.
> `docker compose up -d redis`
>
> 역할·데이터베이스가 아직 없다면 [db/init/01-create-pairing-account.sql](db/init/01-create-pairing-account.sql)을 실행합니다.
> `psql -U postgres -f db/init/01-create-pairing-account.sql`

### 2-1) pgvector 설치 — **AI 매칭을 돌리려면 필수**

AI 매칭은 프리랜서·포지션 임베딩을 `pgvector` 확장으로 저장합니다. **확장이 없으면 추천이 첫
쿼리에서 실패합니다.** `postgres` 공식 이미지에도, 윈도우 설치본에도 기본 포함돼 있지 않습니다.

**윈도우에 PostgreSQL을 직접 설치한 경우 (팀 표준, 2026-08-12)**

1. Visual Studio Build Tools에서 **"C++를 사용한 데스크톱 개발"** 체크해서 설치
2. **관리자 권한**으로 `x64 Native Tools Command Prompt` 실행
3. 아래를 그대로 실행 (`PGROOT`는 설치한 PostgreSQL 경로에 맞출 것)

```bat
set "PGROOT=C:\Program Files\PostgreSQL\18"
git clone https://github.com/pgvector/pgvector.git
cd pgvector
nmake /F Makefile.win
nmake /F Makefile.win install
```

> `nmake`는 PowerShell에는 없습니다. **반드시 `vcvars64.bat`을 `call` 한 cmd** 또는
> `x64 Native Tools Command Prompt`에서 실행하세요. `set "PGROOT=..."` 도 cmd 문법입니다.
> pgvector는 레포 밖(`C:\` 등)에 클론하세요 — 레포 안에 받으면 `git status`에 잡힙니다.

빌드가 끝나면 아래 두 파일이 생깁니다. **생겼으면 성공입니다.**

```
C:\Program Files\PostgreSQL\18\lib\vector.dll
C:\Program Files\PostgreSQL\18\share\extension\vector.control
```

4. `pairing` 데이터베이스에 접속해 확장을 켜고(pgAdmin에서 해도 됩니다), AI 서버 테이블을 만듭니다.

```sql
CREATE EXTENSION vector;
```

```bat
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U pairing -d pairing ^
  -f C:\52_Pairing\Pairing-python\db\init\10-create-ai-schema.sql
```

> `psql`도 PATH에 없어서 전체 경로로 부릅니다. pgAdmin의 Query Tool에서 파일을 열어 실행해도 됩니다.
>
> 실행 중 `ivfflat index created with little data` 경고는 정상입니다 — 빈 테이블이라 그렇습니다.
> 임베딩이 쌓인 뒤 `REINDEX INDEX idx_freelancer_embedding_cosine;` 을 한 번 해주면 됩니다.
>
> 이 SQL은 `ALTER TABLE ... ADD CONSTRAINT` 때문에 **두 번 실행하면 실패합니다**(파괴적이진 않습니다).

**확인** — 아래가 다 나오면 준비 끝입니다.

```sql
SELECT extversion FROM pg_extension WHERE extname = 'vector';   -- 0.8.6 등
SELECT count(*) FROM freelancer_embedding;                       -- 0 (에러만 안 나면 됨)
SELECT count(*) FROM position_embedding;                         -- 0
```

**도커로 PostgreSQL을 쓰는 경우**

빌드가 필요 없습니다. `docker-compose.yml`의 이미지를 `pgvector/pgvector:pg16`으로 바꾸고
(같은 PG16이라 기존 볼륨이 그대로 붙습니다) 컨테이너를 다시 만든 뒤, 위 `10-create-ai-schema.sql`만
실행하면 됩니다.

> ⚠️ **두 방식을 동시에 켜지 마세요.** 도커와 네이티브가 둘 다 5432를 잡으면 앱이 어느 쪽에
> 붙는지 알 수 없습니다(실제로 겪었습니다 — pgAdmin에서 고친 게 앱에 반영 안 되는 식으로 나타납니다).
> 네이티브를 쓰면 `docker compose stop postgres` 후 Redis만 띄우세요: `docker compose up -d redis`

> ⚠️ **임베딩 테이블(`freelancer_embedding`/`position_embedding`)을 자동으로 만드는 코드는
> 어디에도 없습니다.** AI 서버는 `create_all`을 쓰지 않기로 했고(Pairing-python README), 스프링은
> 이 테이블을 JPA 엔티티로 갖고 있지 않습니다. **위 SQL을 사람이 한 번 실행해야 합니다 —
> 배포 DB도 마찬가지입니다.**

프로파일 없이 실행하면 필수 환경변수 두 개가 반드시 필요합니다. 없으면 **기동 단계에서 무엇을 넣어야 하는지 안내와 함께 실패**합니다.

```bash
export JWT_SECRET_KEY=$(openssl rand -base64 48)
export S3_BUCKET=your-bucket-name
./gradlew bootRun
```

`JWT_SECRET_KEY`에 실제 기본값을 두지 않은 것은 공개된 키로 토큰을 서명하는 사고를 막기 위한 의도입니다.
AWS 자격증명은 주입하지 않으면 기본 자격증명 체인(`~/.aws`, IAM 역할)을 사용합니다.

의존 서비스 없이 컨텍스트만 확인하려면 테스트를 실행하면 됩니다 (H2 사용, Redis/S3는 지연 연결이라 기동만 확인).

```bash
./gradlew test
```

### 환경변수

`application.yaml`에 로컬 기본값이 들어있으므로 그대로도 뜨지만, 실제 사용 시에는 아래 값을 주입하세요.
전체 목록과 용도는 [docs/ai/security-guide.md](docs/ai/security-guide.md)에 있습니다.

| 변수 | 설명 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보 (기본값 `jdbc:postgresql://localhost:5432/pairing`, `pairing`/`pairing`) |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 정보 |
| **`JWT_SECRET_KEY`** | **필수.** JWT 서명 키 (HS256이므로 32바이트 이상). 없으면 기동 실패 |
| **`S3_BUCKET`** | **필수.** S3 버킷명. 없으면 기동 실패 |
| `COOKIE_DOMAIN` | 토큰 쿠키 도메인. 비워두면 host-only 쿠키 |
| `COOKIE_SECURE` | https 배포 시 `true` |
| `CORS_ALLOWED_ORIGINS` | 허용 오리진 목록(쉼표 구분) |
| `AWS_REGION` | 기본값 `ap-northeast-2` |
| `S3_CDN_URL` | CloudFront 도메인. 비우면 버킷·리전으로 자동 조합 |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | 비워두면 IAM 역할 등 기본 자격증명 체인 사용 (권장) |

## 문서

| 파일 | 내용 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | AI 작업 가이드 및 요청별 문서 라우팅 |
| [docs/ai/backend-convention.md](docs/ai/backend-convention.md) | 아키텍처·계층별 코드 규칙, global 재사용 목록 |
| [docs/ai/infrastructure-guide.md](docs/ai/infrastructure-guide.md) | S3 업로드와 Redis 사용법 |
| [docs/ai/security-guide.md](docs/ai/security-guide.md) | 시크릿, 환경변수, 인증/인가, 업로드 보안 |
| [docs/ai/testing-guide.md](docs/ai/testing-guide.md) | 검증 명령과 로컬 트러블슈팅 |
| [docs/ai/git-issue-pr-guide.md](docs/ai/git-issue-pr-guide.md) | 브랜치·커밋·PR 규칙 |
| `.ai/` | 진행 상태(STATE), 인수인계(HANDOFF), 작업기록(WORKLOG), API 변경노트 |

## 패키지 구조

```
com.pairing
├── PairingApplication.java
├── global/                        # 도메인에 종속되지 않는 전역 공통 기능
│   ├── annotation/swagger/        # @ApiErrorCodeExample — 에러 응답 예시 자동 문서화
│   ├── aop/                       # ApiLoggingAop — Controller/Service 공통 로깅
│   ├── common/api/response/       # ApiResponse, ErrorResponse, PageResponse
│   ├── config/                    # Async, Swagger, Web, Redis, S3, CdnJackson
│   ├── exception/                 # BaseErrorCode, BusinessException, CommonExceptionAdvice ...
│   ├── filter/                    # TraceIdFilter — 요청별 traceId 발급(MDC + X-Trace-Id)
│   ├── infrastructure/s3/         # S3 어댑터, 버킷 초기화, CDN URL 자동 매핑
│   ├── port/out/                  # FileStoragePort, StorageSettings (아웃바운드 포트)
│   ├── security/                  # JWT 발급/검증 필터, SecurityConfig, 401/403 핸들러
│   ├── type/                      # FileType
│   └── util/                      # RedisKeys, FileTypeDetector
├── auth/                          # 인증(회원가입·로그인·소셜·이메일 인증·계정 복구)
├── account/                       # 계정·프로필·결제수단 애그리거트
├── terms/                         # 약관 조회와 동의 이력
└── example/                       # 새 도메인을 만들 때 복사해서 쓰는 레퍼런스 도메인
    ├── application/
    │   ├── command/               # 입력 DTO (HTTP 기술에 의존하지 않음)
    │   ├── policy/                # 여러 도메인 객체에 걸친 정책
    │   ├── port/                  # 외부에 요구하는 출력 포트
    │   ├── service/               # UseCase 구현 + 트랜잭션 경계
    │   └── usecase/               # Controller가 바라보는 입력 포트
    ├── domain/
    │   ├── event/                 # 도메인 이벤트
    │   ├── model/                 # 순수 도메인 엔티티 (JPA 어노테이션 없음)
    │   └── repository/            # 저장소 포트
    ├── exception/                 # 도메인 에러코드 Enum (BaseErrorCode 구현)
    ├── infrastructure/
    │   ├── event/                 # 이벤트 발행/구독 어댑터
    │   ├── mapper/                # Domain <-> JPA Entity (MapStruct)
    │   └── persistence/           # JPA 엔티티, Spring Data, Repository 어댑터
    ├── presentation/
    │   ├── advice/                # 도메인 전용 @RestControllerAdvice
    │   └── api/                   # 컨트롤러 + request/response DTO
    └── settings/                  # ExampleStorageSettings, 도메인 전용 annotation / aop
```

각 폴더의 `example.txt`는 빈 디렉터리를 형상관리에 남기기 위한 placeholder이며, 해당 폴더의 역할이 한 줄로 적혀 있습니다.

## 아키텍처 규칙

- 의존 방향은 항상 **바깥 → 안쪽**입니다. `presentation → application → domain`, `infrastructure → domain`.
- `domain`은 Spring / JPA를 모릅니다. 순수 자바 객체로만 두고, 불변식 검증을 도메인 안에서 합니다(`Example.validateName` 참고).
- `application`은 `domain/repository`의 **인터페이스(포트)** 에만 의존하고, 구현체는 `infrastructure/persistence`의 어댑터가 제공합니다.
- `presentation`은 Request DTO → Command 변환만 하고 비즈니스 판단을 하지 않습니다.
- 예외는 `BusinessException(도메인ErrorCode)`로 던지면 `CommonExceptionAdvice`가 공통 형식으로 응답합니다.
- 파일은 DB에 **object key(상대경로)만** 저장하고, 절대 URL은 응답 직렬화 시점에 조립합니다(`CdnMappable`). CDN 도메인이 바뀌어도 데이터 마이그레이션이 필요 없습니다.

## 파일 업로드 / Redis 요약

자세한 사용법은 [docs/ai/infrastructure-guide.md](docs/ai/infrastructure-guide.md)에 있습니다.

**업로드** — 서비스는 `FileStoragePort`에만 의존하고, 실제 저장 방식은 `global/infrastructure/s3`의 어댑터가 정합니다.

```java
String key = fileStoragePort.uploadFile(command.image(), storageSettings.getDirectory());
```

응답 DTO에 `implements CdnMappable`을 붙이고 필드명을 `~Url`로 끝내면 `examples/uuid.png` →
`https://my-bucket.s3.ap-northeast-2.amazonaws.com/examples/uuid.png`로 자동 변환됩니다.
동작하는 예시는 `POST /api/v1/examples/{exampleId}/image` 입니다.

**Redis** — 키 접두사는 반드시 `RedisKeys`에 상수로 선언하고, TTL을 항상 지정합니다.

```java
redisTemplate.opsForValue().set(RedisKeys.AUTH_CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);
```

## 새 도메인 추가하기

1. `example` 폴더를 복사해 도메인 이름으로 변경합니다. (예: `product`)
2. 클래스 이름의 `Example` 접두사를 도메인 이름으로 일괄 변경합니다.
3. `XxxErrorCode`의 코드 접두사(`EX_001` 등)를 도메인용으로 바꿉니다.
4. `XxxExceptionAdvice`의 `basePackages`를 새 도메인 경로로 수정합니다.
5. 필요 없는 폴더의 `example.txt`는 삭제하지 말고 그대로 두면 구조가 유지됩니다.

## 프로젝트 이름 바꾸기

현재는 `com.pairing` / `pairing` 입니다. 다시 바꾸려면:

1. `settings.gradle`의 `rootProject.name`
2. `build.gradle`의 `group`, `description`
3. `src/main/java/com/pairing` 디렉터리명과 모든 파일의 `package` / `import` 경로
   (IDE의 Refactor → Rename 사용 권장)
4. `ApiLoggingAop`의 Pointcut 표현식에 박혀있는 베이스 패키지 문자열
5. `application.yaml`의 `spring.application.name`, `logback-spring.xml`의 `LOG_FILE_NAME`
6. `PairingApplication`, `PairingApplicationTests` 클래스명
7. `docker-compose.yml`의 컨테이너명·`POSTGRES_*` 값, `application.yaml`의 `DB_URL` 기본값
8. `docs/ai/backend-convention.md`의 베이스 패키지 표기
