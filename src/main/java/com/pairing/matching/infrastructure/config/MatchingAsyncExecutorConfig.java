package com.pairing.matching.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 매칭 도메인의 {@code @Async} 리스너 전용 스레드 풀.
 *
 * <p>공용 {@code taskExecutor}(core 5 / max 10 / queue 500)를 그대로 쓰면 문제가 있다.
 * {@code ThreadPoolTaskExecutor}는 큐가 꽉 차야만 core 이상으로 스레드를 늘리는데, 큐가 500이라
 * 사실상 <b>동시 5개</b>에서 못 벗어난다. AI 호출(추천·재추천)은 20~60초씩 걸리고, 임베딩 일괄
 * 재색인({@code EmbeddingReindexService})은 분 단위로 스레드 하나를 계속 붙잡는다.
 * 매칭이 이 5개 슬롯을 다 채우면 <b>다른 도메인의 알림 발송 같은 가벼운 {@code @Async} 작업까지
 * 에러도 타임아웃도 없이 뒤로 밀린다</b> — 계약 도메인이 이미 겪고 같은 방식으로 격리했다
 * ({@code ContractDraftExecutorConfig} 참고).
 *
 * <p>풀 크기 4/6은 <b>격리</b>가 목적이지 처리량 극대화가 아니다. 추천·재추천은 AI 호출 동안
 * {@code REQUIRES_NEW} 트랜잭션을 열어 DB 커넥션을 하나 붙잡고 있으므로(HikariCP 기본 10개),
 * 매칭 혼자 커넥션 풀을 다 써버리지 않도록 여유를 둔다.
 *
 * <p>{@code @EnableAsync}는 {@code AsyncConfig}가 전역으로 켜둔다.
 */
@Configuration
public class MatchingAsyncExecutorConfig {

    public static final String EXECUTOR_NAME = "matchingExecutor";

    // 테스트는 SyncTaskExecutorTestConfig가 같은 이름의 동기 실행기를 대신 등록한다
    // (스프링은 이름이 같은 빈 재정의를 기본적으로 막으므로, 이 빈이 먼저 있으면 그쪽을 스킵한다).
    @Bean(EXECUTOR_NAME)
    @ConditionalOnMissingBean(name = EXECUTOR_NAME)
    public Executor matchingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("matching-async-");
        executor.initialize();
        return executor;
    }
}
