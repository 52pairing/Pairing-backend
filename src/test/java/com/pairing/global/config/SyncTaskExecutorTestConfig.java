package com.pairing.global.config;

import com.pairing.matching.infrastructure.config.MatchingAsyncExecutorConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * {@code @Async}를 테스트에서만 같은 스레드에서 실행시킨다.
 *
 * <p>운영에서는 이벤트 리스너가 별도 스레드로 빠져야 결제·이력서 저장 API가 AI 호출을 안 기다리지만
 * ({@code AsyncConfig} 참고), 테스트에서까지 그러면 "호출했는지" 검증이 백그라운드 작업보다 먼저
 * 실행돼서 실패한다. sleep이나 timeout 검증으로 때우면 느리고 CI에서 간헐적으로 깨진다.
 *
 * <p>여기서 검증하려는 건 리스너의 처리 내용이지 스프링의 비동기 동작 자체가 아니므로, 실행 스레드만
 * 동기로 바꿔 결정적으로 만든다. 필요한 테스트 클래스에서 {@code @Import}로 가져다 쓴다.
 */
@TestConfiguration
public class SyncTaskExecutorTestConfig {

    @Bean
    @Primary
    public Executor syncTaskExecutor() {
        return new SyncTaskExecutor();
    }

    /**
     * 매칭 전용 실행기({@code MatchingAsyncExecutorConfig})도 이름으로 직접 지정해 부르므로
     * ({@code @Async(EXECUTOR_NAME)}) 위 {@code @Primary}만으로는 안 바뀐다. 같은 이름으로
     * 동기 실행기를 먼저 등록해 실제 스레드 풀이 안 뜨게 한다.
     */
    @Bean(MatchingAsyncExecutorConfig.EXECUTOR_NAME)
    public Executor matchingExecutor() {
        return new SyncTaskExecutor();
    }
}
