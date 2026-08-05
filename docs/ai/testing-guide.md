# Testing Guide

코드 변경 후 어떤 검증을 돌릴지 고르는 문서입니다.

## 명령

Git Bash / macOS / Linux:

```bash
./gradlew test
```

```bash
./gradlew build
```

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew test
```

## 런타임 정보

- Java: `17`
- Spring Boot: `3.5.14`
- 프로젝트명: `template_server`
- 서버 포트: `8080`
- 운영 DB: PostgreSQL (`org.postgresql:postgresql`), 로컬 DB명 `pairing`
- 테스트 DB: H2 인메모리, PostgreSQL 호환 모드 (`src/test/resources/application.yaml`)
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 로컬 의존 서비스

`./gradlew test`는 H2를 쓰므로 PostgreSQL 없이 통과합니다.

서버를 띄워 확인할 때도 인프라가 필요 없습니다. `local` 프로파일은 인메모리 H2와 개발용 더미 설정을 사용합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

실제 PostgreSQL·Redis로 확인해야 할 때만 컨테이너를 띄웁니다.
(로컬에 PostgreSQL을 직접 설치해 쓰고 있으면 5432가 충돌하므로 `docker compose up -d redis`로 Redis만 띄웁니다)

```bash
docker compose up -d
```

자세한 내용은 `docs/ai/infrastructure-guide.md`를 보십시오.

## 필수 설정값 누락

프로파일 없이 실행하면 `JWT_SECRET_KEY`와 `S3_BUCKET`이 필요합니다.
누락 시 `APPLICATION FAILED TO START` 블록에 설정 키·환경변수명·해결 방법이 함께 출력됩니다.
(`RequiredPropertyFailureAnalyzer`가 처리하며 `META-INF/spring.factories`에 등록되어 있습니다)

새로 필수 설정값을 추가할 때는 `@Value("${키:}")`로 빈 기본값을 두고 코드에서
`RequiredPropertyMissingException`을 던지십시오. 플레이스홀더 치환 자체를 실패시키면
`Unsatisfied dependency` 같은 원인 불명 메시지만 남습니다.

## 언제 무엇을 돌리는가

- 서비스/도메인/매퍼/리포지토리 로직 소폭 변경: 해당 테스트만, 없으면 `test`
- API, 시큐리티, 설정, 영속성, 도메인 간 변경: `test` → 가능하면 `build`
- 의존성, 프로파일, 기동 설정 변경: `build` → 가능하면 `bootRun`
- 명령을 실행할 수 없었다면 그 이유를 최종 응답이나 `.ai/HANDOFF.md`에 남깁니다.

## 테스트 작성 규칙

- 변경된 동작에 집중한 테스트를 우선합니다.
- 공용 기능, API 계약, 영속성 매핑, 시큐리티, 에러 처리를 건드릴 때는 범위를 넓힙니다.
- mock 호출 여부만 확인하는 껍데기 테스트는 그것이 검증 대상일 때만 작성합니다.
- 네이밍은 기존 스타일을 따릅니다.

## 자주 나오는 로컬 문제

### Redis 연결 실패

- Redis 컨테이너가 떠 있는지 확인합니다.
- `REDIS_HOST`, `REDIS_PORT`를 확인합니다.
- Lettuce는 지연 연결이라 기동은 되고 호출 시점에 터집니다.

### DB 연결 실패

- PostgreSQL이 떠 있는지, `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`가 맞는지 확인합니다.
- 데이터베이스(`pairing`)와 역할(`pairing`)이 존재하는지 확인합니다. 없으면 `db/init/01-create-pairing-account.sql`을 실행합니다.
- `permission denied for schema public` 오류가 나면 역할이 데이터베이스 소유자가 아닌 경우입니다.
  PostgreSQL 15부터 `public` 스키마의 CREATE 권한이 PUBLIC에서 회수되었으므로
  `ALTER DATABASE pairing OWNER TO pairing;` 또는 `GRANT ALL ON SCHEMA public TO pairing;`이 필요합니다.

### S3 업로드 실패

- 로그의 `[S3 Upload Error]`에 찍힌 원인(`AccessDenied`, `NoSuchBucket` 등)을 먼저 봅니다.
- 기동 로그의 `[S3]` 줄에서 버킷 접근 확인 결과를 봅니다. 원인별 안내가 함께 찍힙니다.
- `[S3Config]` 줄에서 자격증명이 정적 키인지 기본 체인인지 확인합니다.
- `S3_BUCKET`, `AWS_REGION` 값에 공백/개행이 섞이지 않았는지 확인합니다.

### 시크릿 또는 환경변수 누락

필요한 변수명은 `docs/ai/security-guide.md`에 있습니다. 문서에 실제 값을 쓰지 마십시오.
