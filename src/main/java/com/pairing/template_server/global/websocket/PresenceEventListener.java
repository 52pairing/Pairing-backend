package com.pairing.template_server.global.websocket;

import com.pairing.template_server.global.util.RedisKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STOMP 접속/해제 시점에 온라인 상태를 Redis에 기록하고, 구독자에게 실시간으로 push한다.
 *
 * <p>별도 엔드포인트를 두지 않고 기존 STOMP 연결의 생명주기 이벤트를 그대로 활용한다.
 * 프론트엔드는 폴링 없이 {@code /topic/users/{userId}/presence} 구독만으로 상태 변경을 받는다.
 *
 * <p>누구에게 알릴지는 {@link PresenceSubscriberPort} 가 결정한다. 구현체가 없으면 Redis 기록과
 * 메트릭만 남기고 push는 건너뛴다.
 *
 * <p>{@code userId} 세션 속성이 없으면(= {@link WebSocketUserPort} 미등록) 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    /**
     * 온라인 플래그 TTL.
     *
     * <p>서버 강제 종료처럼 disconnect 이벤트를 받지 못하는 상황에서 키가 영구히 남는 것을 막는 안전장치다.
     * 정상 흐름에서는 해제 시점에 즉시 삭제하므로 TTL에 의존하지 않는다.
     */
    private static final long ONLINE_TTL_HOURS = 24;

    private static final String PRESENCE_DESTINATION = "/topic/users/%d/presence";

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    /** 프로젝트가 구독자 조회를 구현하지 않았을 수 있으므로 지연 조회한다. */
    private final ObjectProvider<PresenceSubscriberPort> presenceSubscriberPortProvider;

    private final AtomicInteger onlineUserCount = new AtomicInteger();

    private Counter connectTotal;
    private Counter disconnectTotal;

    @PostConstruct
    void registerMeters() {
        connectTotal = Counter.builder("websocket.connect.total")
                .description("STOMP 접속 성공 횟수")
                .register(meterRegistry);
        disconnectTotal = Counter.builder("websocket.disconnect.total")
                .description("STOMP 연결 해제 횟수")
                .register(meterRegistry);
        Gauge.builder("websocket.online.users", onlineUserCount, AtomicInteger::get)
                .description("현재 접속 중인 사용자 수 (이 인스턴스 기준)")
                .register(meterRegistry);
    }

    /**
     * CONNECT 프레임 처리가 끝난 뒤 발생하는 이벤트를 받는다.
     *
     * <p>{@code SessionConnectEvent}(처리 시작)가 아니라 {@code SessionConnectedEvent}(처리 완료)를 쓴다.
     * 시작 시점에 카운트하면 CONNECT가 실패한 세션도 온라인으로 집계된다.
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        Long userId = extractUserId(event.getMessage());
        if (userId == null) {
            return;
        }

        redisTemplate.opsForValue()
                .set(RedisKeys.ONLINE_PREFIX + userId, "true", ONLINE_TTL_HOURS, TimeUnit.HOURS);
        onlineUserCount.incrementAndGet();
        connectTotal.increment();
        log.info("[Presence] 온라인 처리: userId={}", userId);

        broadcast(userId, true);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        Long userId = extractUserId(event.getMessage());
        if (userId == null) {
            return;
        }

        redisTemplate.delete(RedisKeys.ONLINE_PREFIX + userId);
        onlineUserCount.updateAndGet(current -> Math.max(0, current - 1));
        disconnectTotal.increment();
        log.info("[Presence] 오프라인 처리: userId={}", userId);

        broadcast(userId, false);
    }

    private void broadcast(Long userId, boolean online) {
        PresenceSubscriberPort subscriberPort = presenceSubscriberPortProvider.getIfAvailable();
        if (subscriberPort == null) {
            return;
        }

        List<Long> subscriberIds = subscriberPort.findSubscriberUserIds(userId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return;
        }

        PresenceEvent payload = new PresenceEvent(userId, online);
        subscriberIds.forEach(subscriberId ->
                messagingTemplate.convertAndSend(PRESENCE_DESTINATION.formatted(subscriberId), payload));
    }

    private Long extractUserId(org.springframework.messaging.Message<?> message) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getSessionAttributes() == null) {
            return null;
        }
        return (Long) accessor.getSessionAttributes().get(WebSocketSessionAttributes.USER_ID);
    }
}
