# Backend Convention

이 프로젝트의 백엔드 코드 규칙입니다.

## 절대 규칙

- 헥사고날(포트-어댑터) 아키텍처를 유지합니다.
- 계층 간 의존 방향을 깨지 않습니다.
- 기존 폴더 구조와 네이밍 스타일을 따릅니다.
- 새 도메인을 추가할 때는 `example` 도메인 패턴에서 시작합니다.
- 코드와 주석에 이모지를 쓰지 않습니다.
- 관련 없는 포매팅, import 정렬, 공백 변경으로 diff를 오염시키지 않습니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.14
- Spring Data JPA + PostgreSQL (`org.postgresql:postgresql`), 테스트는 H2 (PostgreSQL 호환 모드)
- MapStruct 1.5.5 (Domain <-> JPA Entity 매핑), lombok-mapstruct-binding
- Spring Security + JWT (jjwt 0.12.6)
- STOMP over WebSocket (`spring-boot-starter-websocket`, 인메모리 SimpleBroker)
- Redis (`spring-boot-starter-data-redis`)
- AWS S3 (`software.amazon.awssdk:s3`)
- springdoc-openapi (Swagger)
- Actuator + Micrometer
- Lombok, Spring AOP

## 런타임

- 서버 포트: `8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 헬스체크: `http://localhost:8080/actuator/health`

## 베이스 패키지

```text
com.pairing.template_server
```

각 도메인은 베이스 패키지 아래 독립 패키지로 둡니다. 공용 기능은 `global` 아래에 둡니다.

## 표준 도메인 구조

```text
{domain}/
  presentation/
    api/
      request/
      response/
    advice/
  application/
    usecase/
    service/
    command/
    result/
    port/
    policy/
  domain/
    model/
    repository/
    event/
  infrastructure/
    persistence/
    mapper/
    event/
  exception/
  settings/
```

필요한 폴더만 만듭니다. 템플릿 모양을 맞추려고 빈 폴더를 남기지 않습니다.

일반 도메인에 필수인 계층: `presentation`, `application`, `domain`, `infrastructure`, `exception`.

## 의존 방향

```text
presentation  ->  application  ->  domain
                        ^
infrastructure  --------+
```

- 의존은 항상 `domain`을 향해 안쪽으로 흐릅니다.
- `domain`은 Spring, JPA, MapStruct, 바깥 계층을 참조하지 않습니다.
- `application`은 도메인 모델과 포트에 의존하고, 인프라 구현체에 의존하지 않습니다.
- `infrastructure`는 `application` 또는 `domain`이 정의한 포트를 구현합니다.

## Controller 규칙

- `@RestController`, `@RequestMapping("/api/v1/{복수형}")`, `@RequiredArgsConstructor`를 사용합니다.
- 서비스 구현 클래스가 아니라 UseCase 인터페이스만 주입합니다.
- `@Tag`, `@Operation`, `@ApiErrorCodeExample`로 Swagger 메타데이터를 붙입니다.
- 요청 DTO는 `@Valid @RequestBody`로 받습니다. 멀티파트는 `@RequestPart`를 사용합니다.
- 요청 DTO를 즉시 Command record로 변환합니다.
- `ResponseEntity<ApiResponse<T>>`를 반환합니다.
- 컨트롤러에 비즈니스 로직을 두지 않습니다.

## DTO 규칙

- 요청/응답 DTO는 Java `record`를 사용합니다.
- `@Schema`로 설명과 예시를 붙입니다.
- 요청 검증은 `jakarta.validation` 애노테이션을 사용합니다.
- application의 Command record에 HTTP/Swagger 관심사를 넣지 않습니다.
- 파일 URL을 내려주는 응답 DTO는 `CdnMappable`을 구현하고 필드명을 `~Url`/`~Urls`로 끝냅니다.

## UseCase 규칙

- 인바운드 포트는 `application/usecase`의 인터페이스로 정의합니다.
- 메서드는 Command record를 받고 ID, 결과 DTO, 도메인 결과를 반환합니다.

## Service 규칙

- `@Service`, `@Transactional`, `@RequiredArgsConstructor`를 사용합니다.
- UseCase 인터페이스를 구현합니다.
- 조회 전용 메서드에는 `@Transactional(readOnly = true)`를 붙입니다.
- 일반적인 흐름:
  1. 입력 검증
  2. 도메인 모델 로드 또는 생성
  3. 도메인 메서드로 상태 변경
  4. 리포지토리 포트로 저장
- 예외는 `BusinessException(도메인ErrorCode)`로 던집니다.

## Domain Model 규칙

- 도메인 모델은 순수 POJO로 유지합니다. JPA 애노테이션을 붙이지 않습니다.
- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용합니다.
- 생성은 정적 팩토리로만 하고 생성자는 private으로 닫습니다.
  - `create(...)`: 새 객체 생성 + 불변식 검증
  - `reconstitute(...)`: 영속 데이터 복원
- setter를 노출하지 않습니다. `changeName`, `deactivate`처럼 의미 있는 메서드로 상태를 바꿉니다.
- 불변식 검증은 private 검증 메서드 안에 둡니다.

## Repository Port 규칙

- 리포지토리 포트는 인터페이스로 정의합니다.
- JPA 엔티티가 아니라 도메인 모델 타입을 사용합니다.
- 필요에 따라 `Optional`, `List`, 도메인 결과 타입을 반환합니다.

## Persistence 규칙

- JPA 엔티티 네이밍: `{Domain}JpaEntity`
- Spring Data 인터페이스 네이밍: `SpringData{Domain}Repository`
- 어댑터 네이밍: `{Domain}RepositoryAdapter`
- `@Entity`, `@Table`, `@Getter`, protected 기본 생성자를 사용합니다.
- PK는 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`를 기본으로 합니다.
- JPA 엔티티와 도메인 모델을 분리합니다. 변환은 MapStruct 매퍼가 담당합니다.
- 파일 경로는 **object key(상대경로)** 만 저장합니다. CDN 절대 URL을 DB에 넣지 않습니다.

## Mapper 규칙

- `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)`을 사용합니다.
- `toJpaEntity(domain)`, `toDomain(entity)` 두 방향을 정의합니다.
- 도메인 생성자가 닫혀 있으면 default 메서드에서 `reconstitute(...)`를 호출합니다.
- null을 방어적으로 처리합니다.

## Exception 규칙

- 도메인 에러코드 네이밍: `{Domain}ErrorCode`
- 에러코드 enum은 `BaseErrorCode`를 구현하고 `HttpStatus status`, `String code`, `String message`를 가집니다.
- 코드 형식은 도메인 약어 + 번호를 사용합니다. 예: `EX_001`
- 도메인 전용 예외 처리는 `presentation/advice`에 두고, 범위를 해당 도메인 컨트롤러 패키지로 제한합니다.
- 공통 처리(BusinessException, Validation, 400/404/405/500)는 `CommonExceptionAdvice`의 default 메서드가 담당하므로 다시 구현하지 않습니다.

## Global 재사용 우선

새 인프라를 만들기 전에 `global`을 먼저 확인합니다.

| 기능 | 위치 |
| --- | --- |
| 성공/에러/페이지 응답 | `global/common/api/response` |
| 비즈니스 예외, 에러코드 계약, 공통 예외 처리 | `global/exception` |
| Swagger 에러코드 문서화 | `global/annotation/swagger` + `global/config/SwaggerConfig` |
| JWT 발급/검증, 시큐리티 설정, 401/403 응답 | `global/security` |
| 파일 업로드/삭제 | `global/port/out/FileStoragePort` (구현: `global/infrastructure/s3`) |
| 파일 URL 자동 변환 | `global/infrastructure/s3/CdnMappable` |
| Redis 접근 | `global/config/RedisConfig` + `global/util/RedisKeys` |
| WebSocket(STOMP) 브로커·핸드셰이크 인증 | `global/config/StompWebSocketConfig` + `global/websocket` |
| 요청 추적 ID | `global/filter/TraceIdFilter` |
| 공통 로깅 | `global/aop/ApiLoggingAop` |
| 비동기 실행 | `global/config/AsyncConfig` |
| 파일 종류 판별 | `global/util/FileTypeDetector` + `global/type/FileType` |

## 새 기능 체크리스트

1. 어느 도메인이 이 기능을 소유하는지 결정합니다.
2. 새 도메인이 필요하면 `example` 패턴을 복사해서 시작합니다.
3. `domain/model`에 비즈니스 규칙을 먼저 모델링합니다.
4. 리포지토리 포트를 정의합니다.
5. UseCase 인터페이스를 정의합니다.
6. 애플리케이션 서비스를 구현합니다.
7. JPA 엔티티, Spring Data 인터페이스, 어댑터, 매퍼를 추가합니다.
8. 컨트롤러와 request/response record를 추가합니다.
9. 에러코드를 추가하거나 갱신합니다.
10. Swagger 메타데이터를 추가합니다.
11. 의존 방향과 폴더 구조를 검증합니다.
12. 관련 테스트나 빌드 명령을 실행합니다.
