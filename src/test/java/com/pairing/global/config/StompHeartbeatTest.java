package com.pairing.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STOMP 브로커가 하트비트를 켜고 올라오는지 검증한다.
 *
 * <p>하트비트가 꺼져 있으면 서버는 {@code CONNECTED} 에 {@code heart-beat:0,0} 을 실어 보내고,
 * STOMP 규약상 한쪽이 0이면 양방향 모두 비활성이라 프론트가 {@code 10000,10000} 을 요청해도
 * 프레임이 하나도 흐르지 않는다. 그러면 협상의 AI 대기 구간(조건마다 15초 안팎)에 연결이
 * 완전히 idle 이 되어 ALB 가 60초에 끊고, 클라이언트는 끊긴 줄 모른 채 타결 이벤트를 놓친다.
 * 실제로 그 상태를 재현해 확인했다(2026-08-11).
 *
 * <p>설정 한 줄이 빠져도 애플리케이션은 멀쩡히 뜨고 화면도 정상으로 보인다. 오래 조용한
 * 뒤에야 드러나기 때문에 사람 손 테스트로는 잘 안 잡힌다. 그래서 테스트로 고정한다.
 */
@SpringBootTest
class StompHeartbeatTest {

    /** ALB 대상 그룹 idle timeout. 하트비트 주기는 이보다 충분히 짧아야 한다. */
    private static final long ALB_IDLE_TIMEOUT_MS = 60_000L;

    @Autowired
    @Qualifier("simpleBrokerMessageHandler")
    AbstractBrokerMessageHandler brokerMessageHandler;

    @Test
    @DisplayName("브로커 하트비트가 양방향으로 켜져 있고 ALB idle timeout보다 짧다")
    void heartbeatIsEnabledAndShorterThanIdleTimeout() {
        SimpleBrokerMessageHandler broker = (SimpleBrokerMessageHandler) brokerMessageHandler;

        long[] heartbeat = broker.getHeartbeatValue();

        assertThat(heartbeat).isNotNull();
        assertThat(heartbeat[0])
                .as("서버가 클라이언트로 보내는 주기")
                .isGreaterThan(0)
                .isLessThan(ALB_IDLE_TIMEOUT_MS);
        assertThat(heartbeat[1])
                .as("서버가 클라이언트에게 기대하는 수신 주기")
                .isGreaterThan(0)
                .isLessThan(ALB_IDLE_TIMEOUT_MS);
    }

    @Test
    @DisplayName("하트비트를 돌릴 스케줄러가 붙어 있다")
    void heartbeatSchedulerIsWired() {
        SimpleBrokerMessageHandler broker = (SimpleBrokerMessageHandler) brokerMessageHandler;

        // 스케줄러가 없으면 하트비트 값이 있어도 실제로 프레임을 보내지 못한다.
        assertThat(broker.getTaskScheduler()).isNotNull();
    }
}
