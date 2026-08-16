package com.pairing.contract.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 계약서 문구 채우기 전용 스레드 풀.
 *
 * <p>공용 {@code taskExecutor}(core 5 / max 10)를 쓰지 않는 이유는, 파이썬이 죽었을 때
 * 복구 배치가 각각 최대 120초씩(AI 읽기 타임아웃) 물어 공용 스레드를 오래 잠그기 때문이다.
 * 그러면 알림 발송 같은 다른 {@code @Async} 작업이 뒤로 밀린다.
 *
 * <p>풀 크기 2 는 처리량이 아니라 <b>격리와 상한</b>이 목적이다. AI 호출이 트랜잭션 안에서
 * 일어나므로 이 값이 곧 계약이 붙잡을 수 있는 DB 커넥션 수의 상한이 된다(Hikari 기본 10개).
 * 밀린 계약은 다음 복구 주기에 다시 집으므로 처리량을 여기서 키울 이유가 없다.
 *
 * <p>{@code @EnableAsync} 는 {@code AsyncConfig} 가 전역으로 켜둔다.
 */
@Configuration
public class ContractDraftExecutorConfig {

    public static final String EXECUTOR_NAME = "contractDraftExecutor";

    @Bean(EXECUTOR_NAME)
    public Executor contractDraftExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        // 복구 배치 상한(20)보다 넉넉히 잡는다. 넘쳐 거부돼도 다음 주기에 다시 집는다.
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("contract-draft-");
        executor.initialize();
        return executor;
    }
}
