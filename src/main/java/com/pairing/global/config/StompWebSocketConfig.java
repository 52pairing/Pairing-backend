package com.pairing.global.config;

import com.pairing.global.websocket.JwtHandshakeInterceptor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * STOMP over WebSocket 설정.
 *
 * <p>경로 규칙
 * <ul>
 *   <li>{@code /ws} — 핸드셰이크 엔드포인트. 인증은 {@link JwtHandshakeInterceptor} 가 담당한다.</li>
 *   <li>{@code /topic/**} — 서버에서 클라이언트로 push하는 구독 경로</li>
 *   <li>{@code /app/**} — 클라이언트가 서버로 보낼 때 붙이는 prefix. {@code @MessageMapping} 이 받는다.</li>
 *   <li>{@code /user/**} — 특정 사용자에게만 보낼 때 사용한다. ({@code convertAndSendToUser})</li>
 * </ul>
 *
 * <p>브로커는 인메모리 SimpleBroker다. 서버를 여러 대로 늘리면 인스턴스 간 push가 전달되지 않으므로,
 * 그 시점에 외부 브로커(Redis Pub/Sub 릴레이, RabbitMQ STOMP 등)로 교체해야 한다.
 *
 * <p>허용 오리진은 REST와 같은 값({@code app.cors.allowed-origins})을 쓴다. WebSocket 핸드셰이크는
 * 시큐리티의 CORS 설정을 타지 않으므로 여기서 따로 지정해야 한다.
 *
 * <p>메시지 핸들러 추가 예시
 * <pre>{@code
 * @Controller
 * @RequiredArgsConstructor
 * public class ChatMessageHandler {
 *
 *     private final SimpMessagingTemplate messagingTemplate;
 *
 *     // 클라이언트: stompClient.send("/app/chat/rooms/1", {}, body)
 *     @MessageMapping("/chat/rooms/{roomId}")
 *     public void send(@DestinationVariable Long roomId,
 *                      @Payload ChatMessageSendRequest request,
 *                      SimpMessageHeaderAccessor accessor) {
 *         Long userId = (Long) accessor.getSessionAttributes().get(WebSocketSessionAttributes.USER_ID);
 *         messagingTemplate.convertAndSend("/topic/chat/rooms/" + roomId, ...);
 *     }
 * }
 * }</pre>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * 배포 환경에서 <b>항상</b> 여는 핸드셰이크 경로.
     *
     * <p>{@code /api/*} 는 ALB 리스너 규칙과 무관하게 늘 백엔드로 온다. 그래서 이 경로만은
     * 설정값과 상관없이 고정으로 등록한다. 설정에 의존하면 환경변수 한 줄이 바뀔 때마다
     * 실시간이 통째로 죽는데, 그게 실제로 일어났다 — 태스크 정의의 {@code WEBSOCKET_ENDPOINT=/ws}
     * 가 코드 기본값을 덮어써서 프론트가 붙을 곳이 사라졌다.
     */
    private static final String ALWAYS_ON_ENDPOINT = "/api/ws";

    /**
     * 추가 핸드셰이크 경로(쉼표로 여러 개). 바꾸면 GlobalSecurityConfig의 permitAll 경로도 맞춰야 한다.
     *
     * <p>{@link #ALWAYS_ON_ENDPOINT} 는 여기 없어도 항상 등록된다. 이 값은 그 밖의 경로를
     * 더 열 때만 쓴다(예: 구버전 프론트가 아직 쓰는 {@code /ws}).
     */
    @Value("${app.websocket.endpoint}")
    private String endpoints;

    /** REST와 동일한 허용 오리진 목록을 사용한다. */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * 하트비트 주기(ms). {서버가 보내는 주기, 서버가 기대하는 수신 주기}.
     *
     * <p>ALB 대상 그룹의 idle timeout(60초)보다 충분히 짧아야 한다. 협상은 A2A 왕복이
     * 조건마다 15초 안팎이라 <b>사람도 대리인도 아무것도 보내지 않는 구간이 1분을 쉽게 넘긴다.</b>
     * 그 사이 연결이 끊기면 클라이언트는 끊긴 줄 모른 채 타결 이벤트를 놓친다.
     */
    private static final long[] HEARTBEAT = {10_000L, 10_000L};

    /**
     * 하트비트 전용 스케줄러. <b>일부러 빈으로 등록하지 않는다.</b>
     *
     * <p>스프링이 이미 {@code messageBrokerTaskScheduler} 를 등록해 두는데, 그게 이 컨텍스트의
     * 유일한 {@link TaskScheduler} 라서 {@code @Scheduled}(매칭 만료 배치 등)도 그걸 쓰고 있다.
     * 여기서 스케줄러를 {@code @Bean} 으로 하나 더 올리면 타입이 둘이 되어 {@code @Scheduled}
     * 의 스케줄러 해석이 모호해지고, 배치가 조용히 다른 스레드 풀로 옮겨간다. 하트비트 하나
     * 켜자고 건드릴 범위가 아니다.
     *
     * <p>기존 {@code messageBrokerTaskScheduler} 를 주입받는 방법도 있지만, 그 빈은 이 클래스를
     * 설정자로 수집하는 구성 클래스가 만든다 — 생성자 주입하면 순환이 된다.
     */
    private final ThreadPoolTaskScheduler heartbeatScheduler = createHeartbeatScheduler();

    private static ThreadPoolTaskScheduler createHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @PreDestroy
    void shutdownHeartbeatScheduler() {
        heartbeatScheduler.shutdown();
    }

    /**
     * 브로커 설정.
     *
     * <p>{@link #HEARTBEAT} 를 켜지 않으면 서버가 {@code CONNECTED} 에 {@code heart-beat:0,0}
     * 을 실어 보낸다. STOMP 규약상 <b>한쪽이 0이면 양방향 모두 비활성</b>이라, 프론트가
     * {@code 10000,10000} 을 요청해도 결과적으로 아무 프레임도 흐르지 않는다.
     * 실제로 그 상태에서 타결 이벤트가 상대 창에 도달하지 않는 것을 확인했다(2026-08-11).
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(HEARTBEAT)
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS 폴백은 켜지 않는다. 필요하면 .withSockJS() 를 붙이되, 프론트엔드도 SockJS 클라이언트를 써야 한다.
        registry.addEndpoint(parseEndpoints())
                .setAllowedOriginPatterns(parseOrigins())
                .addInterceptors(jwtHandshakeInterceptor);
    }

    /**
     * 등록할 핸드셰이크 경로들 = {@link #ALWAYS_ON_ENDPOINT} + 설정값(쉼표 구분).
     *
     * <p>설정이 비어 있거나 {@code /api/ws} 를 빠뜨려도 그 경로는 반드시 들어간다.
     */
    private String[] parseEndpoints() {
        return Stream.concat(
                        Stream.of(ALWAYS_ON_ENDPOINT),
                        Arrays.stream(endpoints.split(",")).map(String::trim))
                .filter(path -> !path.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] parseOrigins() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        return origins.toArray(String[]::new);
    }
}
