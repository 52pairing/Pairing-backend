# Infrastructure Guide

Redis와 S3(오브젝트 스토리지) 사용법입니다. 새로 만들지 말고 여기 있는 것을 재사용하십시오.

## 로컬 의존 서비스 띄우기

프로젝트 루트에서:

```bash
docker compose up -d
```

| 서비스 | 주소 | 비고 |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | DB명 `pairing`, 역할 `pairing` / 비밀번호 `pairing` |
| Redis | `localhost:6379` | 비밀번호 없음 |

파일 저장소는 로컬 대체 컨테이너 없이 **실제 AWS S3**를 사용합니다. 로컬 개발용 버킷을 따로 만들어 `S3_BUCKET`으로 지정하십시오.

---

## 파일 업로드 (AWS S3)

### 구조

```text
global/port/out/FileStoragePort        # application 계층이 의존하는 포트
global/port/out/StorageSettings        # 도메인별 저장 디렉터리(key prefix) 계약
global/infrastructure/s3/
  S3Settings                           # 버킷, 리전, 응답 URL 루트 해석
  S3StorageAdapter                     # FileStoragePort 구현체
  S3BucketInitializer                  # 기동 시 버킷 접근 확인 (생성하지 않음)
  CdnMappable / NoCdnUrl               # 응답 DTO의 URL 필드 자동 변환 마커
  CdnUrlSerializer / ...Modifier       # object key -> 절대 URL 변환
global/config/S3Config                 # S3Client 빈, 자격증명 결정
global/config/CdnJacksonConfig         # 웹 ObjectMapper에만 CDN 변환 등록
```

### 핵심 규칙

- **DB에는 object key(상대경로)만 저장합니다.** 예: `examples/3f9a....png`
- 절대 URL은 HTTP 응답 직렬화 시점에만 조립됩니다. CDN 도메인이 바뀌어도 데이터 마이그레이션이 필요 없습니다.
- 버킷은 전역 단일 버킷입니다. 도메인 구분은 key prefix(디렉터리)로 합니다.
- 원본 파일명을 저장 경로에 쓰지 않습니다. 어댑터가 UUID로 새 이름을 만듭니다.
- 파일 종류 검증은 `FileTypeDetector`를 사용합니다. MIME/확장자는 클라이언트가 보낸 값이라 보안 목적이라면 시그니처 검사를 추가해야 합니다.
- 엔드포인트는 지정하지 않습니다. SDK가 리전에서 자동으로 결정합니다. (MinIO 같은 S3 호환 스토리지는 지원하지 않습니다)
- 버킷은 애플리케이션이 만들지 않습니다. 권한·수명주기·퍼블릭 액세스 정책을 함께 정해야 하므로 인프라 쪽에서 미리 생성합니다.

### 사용법

1. 도메인에 저장 디렉터리를 선언합니다.

```java
@Getter
@Component
public class ExampleStorageSettings implements StorageSettings {
    @Value("${example.storage.directory:examples}")
    private String directory;
}
```

2. 서비스에서 포트로 업로드합니다.

```java
String uploadedKey = fileStoragePort.uploadFile(command.image(), storageSettings.getDirectory());
String previousKey = example.changeImage(uploadedKey);
exampleRepository.save(example);

// 교체된 기존 파일은 정리한다 (삭제 실패는 어댑터가 로깅만 하고 넘어감)
if (previousKey != null && !previousKey.equals(uploadedKey)) {
    fileStoragePort.deleteFile(previousKey);
}
```

3. 응답 DTO에 `CdnMappable`을 붙이고 필드명을 `~Url`로 끝냅니다.

```java
public record ExampleResponse(Long exampleId, String imageUrl) implements CdnMappable {}
```

`imageUrl`에 `examples/uuid.png`가 들어있으면 응답에는
`https://my-bucket.s3.ap-northeast-2.amazonaws.com/examples/uuid.png`로 나갑니다.

이름 규칙에는 걸리지만 변환하면 안 되는 필드(외부 링크 등)에는 `@NoCdnUrl`을 붙입니다.

전체 예시는 `example` 도메인의 `POST /api/v1/examples/{exampleId}/image`를 참고하십시오.

### 설정

| 환경변수 | 필수 | 설명 |
| --- | --- | --- |
| `S3_BUCKET` | O | 버킷명. 없으면 기동에 실패합니다 |
| `AWS_REGION` | | 기본값 `ap-northeast-2` |
| `S3_CDN_URL` | | CloudFront 도메인. 비우면 `https://{bucket}.s3.{region}.amazonaws.com` |
| `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` | | 비우면 AWS 기본 자격증명 체인 사용 |

**자격증명은 되도록 주입하지 마십시오.** 키를 비워두면 SDK가 환경변수 → `~/.aws/credentials` →
EC2/ECS IAM 역할 순으로 찾습니다. 배포 환경에서는 태스크·인스턴스 역할을 쓰는 것이 키를 환경변수로 심는 것보다 안전하고,
교체(rotation)도 자동으로 처리됩니다. 기동 로그의 `[S3Config]` 줄에서 어느 방식이 선택됐는지 확인할 수 있습니다.

### 기동 시 진단

`S3BucketInitializer`가 버킷 접근을 확인하고, 실패하면 원인별로 안내 로그를 남깁니다. 기동을 막지는 않습니다.

| 로그 | 확인할 것 |
| --- | --- |
| 버킷이 존재하지 않습니다 | `S3_BUCKET` 오타 |
| 접근 권한이 없습니다 | IAM 정책 (`s3:ListBucket`, `s3:PutObject`, `s3:DeleteObject`) |
| 다른 리전에 있습니다 | `AWS_REGION` |
| 버킷 확인 실패 | 자격증명 미설정 또는 네트워크 차단 |

### 파일 업로드를 쓰지 않는다면

`S3_BUCKET`이 필수이므로 빈 값으로는 기동되지 않습니다. 업로드 기능이 필요 없다면 아래를 삭제하십시오.

```text
global/infrastructure/s3/    global/port/out/    global/type/FileType.java
global/util/FileTypeDetector.java    global/config/S3Config.java    global/config/CdnJacksonConfig.java
example/settings/ExampleStorageSettings.java
application.yaml의 cloud.aws 블록
```

`ExampleController`의 이미지 업로드 엔드포인트와 `ExampleCommandService`의 업로드 메서드도 함께 지웁니다.

---

## Redis

### 구조

```text
global/config/RedisConfig    # RedisTemplate<String,String>, RedisTemplate<String,Object>
global/util/RedisKeys        # 키 접두사 상수
```

### 핵심 규칙

- 키 접두사는 문자열 리터럴로 흩뿌리지 않고 **반드시 `RedisKeys`에 상수로 선언**합니다. 오타로 read/write 키가 어긋나도 컴파일 에러가 나지 않습니다.
- 값 저장 시 **TTL을 항상 지정**합니다. TTL 없는 키는 영구히 남아 메모리를 잠식합니다.
- 단순 문자열은 `RedisTemplate<String, String>`, 객체(JSON)는 `RedisTemplate<String, Object>`(`objectRedisTemplate`)를 씁니다.
- 객체 템플릿은 **웹 ObjectMapper와 별개의 전용 ObjectMapper**를 씁니다. 두 가지를 동시에 만족시켜야 하기 때문입니다.
  - 웹 매퍼에는 `CdnUrlSerializerModifier`가 붙어 있어 그대로 쓰면 캐시에 object key가 아니라 CDN 절대 URL이 저장됩니다. CDN 주소를 바꾸면 캐시가 전부 무효가 됩니다.
  - 캐시는 꺼낼 때 원래 타입으로 복원하려면 `@class` 타입 정보(default typing)가 필요한데, 이걸 웹 응답에 켜면 모든 API 응답에 `@class`가 노출됩니다.
- 이 두 가지는 `RedisSerializationTest`가 지키고 있습니다. `RedisConfig`를 수정하면 이 테스트를 반드시 확인하십시오.
  - default typing을 끄면 값이 `LinkedHashMap`으로 복원되어 캐스팅 지점에서 `ClassCastException`이 납니다.
  - 타입 허용 목록은 애플리케이션 패키지 + `java.util` + `java.time`으로 제한되어 있습니다. 다른 패키지의 객체를 캐싱하려면 `RedisConfig`의 허용 목록을 넓혀야 합니다.

### 사용법

```java
private final RedisTemplate<String, String> redisTemplate;

// 저장 (TTL 필수)
redisTemplate.opsForValue().set(
        RedisKeys.AUTH_CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);

// 조회
String saved = redisTemplate.opsForValue().get(RedisKeys.AUTH_CODE_PREFIX + email);

// 삭제
redisTemplate.delete(RedisKeys.AUTH_CODE_PREFIX + email);
```

객체를 여러 빈에서 주입할 때는 이름을 명시합니다.

```java
public MyService(@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) { ... }
```

### 주의

- Lettuce는 지연 연결이므로 Redis가 꺼져 있어도 애플리케이션은 기동됩니다. 실제 호출 시점에 예외가 납니다.
- 따라서 Redis를 쓰는 테스트는 로컬 Redis 또는 Testcontainers를 준비해야 합니다.

## WebSocket (STOMP)

### 구조

```text
global/config/StompWebSocketConfig      브로커·엔드포인트 설정 (@EnableWebSocketMessageBroker)
global/websocket/
  JwtHandshakeInterceptor               핸드셰이크에서 JWT 검증 후 세션 속성에 사용자 정보 저장
  WebSocketSessionAttributes            세션 속성 키 상수 (userId / principal / role)
  WebSocketUserPort                     subject -> 사용자 PK 조회 포트 (구현 선택)
  PresenceSubscriberPort                상태 변경을 알릴 대상 조회 포트 (구현 선택)
  PresenceEventListener                 접속/해제 시 Redis 온라인 플래그 + 구독자 push + 메트릭
  PresenceEvent                         push 페이로드 record
```

### 경로 규칙

| 경로 | 용도 |
| --- | --- |
| `/ws` | 핸드셰이크 엔드포인트 (`app.websocket.endpoint`) |
| `/topic/**` | 서버 -> 클라이언트 구독 경로 |
| `/queue/**` | 1:1 전달용 구독 경로 |
| `/app/**` | 클라이언트 -> 서버 전송 prefix. `@MessageMapping`이 받습니다 |
| `/user/**` | `convertAndSendToUser`로 특정 사용자에게만 보낼 때 |

### 핵심 규칙

- **인증은 핸드셰이크에서 한 번만** 합니다. WebSocket 프레임에는 시큐리티 필터가 걸리지 않으므로
  `/ws/**`는 `GlobalSecurityConfig`에서 permitAll이고, 실제 검증은 `JwtHandshakeInterceptor`가 합니다.
  엔드포인트 경로를 바꾸면 permitAll 경로도 함께 바꿔야 합니다.
- 토큰은 쿼리 파라미터가 아니라 `accessToken` **쿠키**에서 읽습니다. 쿼리로 받으면 접속 URL이
  액세스 로그와 Referer에 남습니다.
- 메시지 핸들러에서 사용자를 식별할 때는 세션 속성을 씁니다. 문자열 리터럴 대신
  `WebSocketSessionAttributes` 상수를 사용합니다.
- 브로커는 **인메모리 SimpleBroker**입니다. 서버를 여러 대로 늘리면 인스턴스 간 push가 전달되지 않으므로
  그 시점에 외부 브로커(RabbitMQ STOMP 릴레이 등)로 교체해야 합니다.

### 사용법

메시지 핸들러는 각 도메인의 `presentation`에 둡니다.

```java
@Controller
@RequiredArgsConstructor
public class ChatMessageHandler {

    private final SimpMessagingTemplate messagingTemplate;

    // 클라이언트: stompClient.send("/app/chat/rooms/1", {}, body)
    @MessageMapping("/chat/rooms/{roomId}")
    public void send(@DestinationVariable Long roomId,
                     @Payload ChatMessageSendRequest request,
                     SimpMessageHeaderAccessor accessor) {
        Long userId = (Long) accessor.getSessionAttributes().get(WebSocketSessionAttributes.USER_ID);
        messagingTemplate.convertAndSend("/topic/chat/rooms/" + roomId, ...);
    }
}
```

서버에서 임의 시점에 push할 때는 `SimpMessagingTemplate`만 주입하면 됩니다.

### 포트 구현 (선택)

두 포트는 구현체가 없어도 애플리케이션이 기동됩니다. 골격만 들어 있는 상태이므로 필요한 기능에 맞춰 등록합니다.

| 포트 | 미구현 시 동작 | 구현하면 |
| --- | --- | --- |
| `WebSocketUserPort` | `userId` 세션 속성이 null. 접속은 허용됨 | 사용자 PK를 세션에 담고, 조회 실패 시 핸드셰이크 거절 |
| `PresenceSubscriberPort` | Redis 플래그·메트릭만 기록하고 push 없음 | 구독자에게 `/topic/users/{userId}/presence`로 상태 변경 push |

`PresenceEventListener`는 `userId` 세션 속성이 없으면 아무것도 하지 않습니다. 온라인 상태 기능을 쓰려면
`WebSocketUserPort` 구현이 먼저 필요합니다.

### 설정

```yaml
app:
  websocket:
    endpoint: ${WEBSOCKET_ENDPOINT:/ws}
  cors:
    # 핸드셰이크 허용 오리진도 이 값을 함께 사용합니다.
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://127.0.0.1:3000}
```

### 메트릭

`PresenceEventListener`가 기동 시 등록합니다. Redis 없이도 컨텍스트는 뜨지만 접속 처리 시점에 Redis를 씁니다.

| 이름 | 종류 | 의미 |
| --- | --- | --- |
| `websocket.connect.total` | Counter | STOMP 접속 성공 횟수 |
| `websocket.disconnect.total` | Counter | 연결 해제 횟수 |
| `websocket.online.users` | Gauge | 현재 접속 사용자 수 (해당 인스턴스 기준) |

### WebSocket을 쓰지 않는다면

`global/websocket` 디렉터리와 `global/config/StompWebSocketConfig`를 지우고,
`build.gradle`의 `spring-boot-starter-websocket`, `GlobalSecurityConfig`의 `/ws/**` permitAll,
`application.yaml`의 `app.websocket`, `RedisKeys.ONLINE_PREFIX`를 함께 제거합니다.
