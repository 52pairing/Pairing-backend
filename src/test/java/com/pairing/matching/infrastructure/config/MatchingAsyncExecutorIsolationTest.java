package com.pairing.matching.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "매칭 전용 스레드 풀 분리"(최적화 2번)의 개선 전/후를 <b>실제 스레드 풀 동작</b>으로 실측한다.
 *
 * <p>공용 {@code taskExecutor}는 core 5 / max 10 / queue 500이라, 큐가 꽉 차야만 core 이상으로
 * 늘어난다(사실상 동시 5개). 이 테스트는 그 5개를 다른 도메인 작업으로 흉내 내 점유시킨 뒤,
 * <b>같은 순간</b> 공용 풀에 넣은 작업(개선 전과 동일한 처지)과 {@code matchingExecutor}에 넣은
 * 작업(개선 후)이 실제로 얼마나 빨리 시작되는지를 잰다. `@SpringBootTest`만 쓰고
 * {@code SyncTaskExecutorTestConfig}는 일부러 import하지 않는다 — 진짜 스레드 풀 동작을 봐야 한다.
 */
@SpringBootTest
class MatchingAsyncExecutorIsolationTest {

    @Autowired
    @Qualifier("taskExecutor")
    private Executor sharedExecutor;

    @Autowired
    @Qualifier(MatchingAsyncExecutorConfig.EXECUTOR_NAME)
    private Executor matchingExecutor;

    @Test
    @DisplayName("공용 풀이 다른 도메인 작업으로 꽉 찼을 때, matchingExecutor 작업은 그래도 즉시 시작된다")
    void matchingExecutorStaysResponsiveWhileSharedPoolIsSaturated() throws InterruptedException {
        int saturating = 5; // 공용 풀 core 크기와 동일 — "다른 도메인이 5개를 다 쓰고 있다" 상황

        CountDownLatch saturatingStarted = new CountDownLatch(saturating);
        CountDownLatch releaseSaturating = new CountDownLatch(1);
        for (int i = 0; i < saturating; i++) {
            sharedExecutor.execute(() -> {
                saturatingStarted.countDown();
                awaitQuietly(releaseSaturating);
            });
        }
        // 공용 풀 core 5개가 전부 다른(가짜) 작업으로 점유됐음을 확인한다 — 이게 "개선 전" 조건이다.
        assertThat(saturatingStarted.await(5, TimeUnit.SECONDS))
                .as("공용 풀 5개 슬롯이 전부 점유돼야 이 테스트의 전제가 성립한다")
                .isTrue();

        long t0 = System.nanoTime();

        CountDownLatch sharedTaskRan = new CountDownLatch(1);
        AtomicLong sharedTaskStartedAtNanos = new AtomicLong(-1);
        sharedExecutor.execute(() -> {
            sharedTaskStartedAtNanos.set(System.nanoTime());
            sharedTaskRan.countDown();
        });

        CountDownLatch matchingTaskRan = new CountDownLatch(1);
        AtomicLong matchingTaskStartedAtNanos = new AtomicLong(-1);
        matchingExecutor.execute(() -> {
            matchingTaskStartedAtNanos.set(System.nanoTime());
            matchingTaskRan.countDown();
        });

        // matchingExecutor는 별도 풀이라 공용 풀 점유와 무관하게 곧바로 시작돼야 한다.
        assertThat(matchingTaskRan.await(500, TimeUnit.MILLISECONDS))
                .as("matchingExecutor는 공용 풀이 막혀 있어도 즉시 실행돼야 한다")
                .isTrue();
        // 공용 풀에 넣은 작업은 core 5개가 다 막혀 있고 max(10)는 큐(500)가 차야만 늘어나므로,
        // 이 시점엔 아직 시작 못 하고 큐에서 대기 중이어야 한다 — "개선 전" 증상 그대로다.
        assertThat(sharedTaskRan.await(300, TimeUnit.MILLISECONDS))
                .as("공용 풀 6번째 작업은 이 시점에 아직 대기 중이어야 한다(= 개선 전 증상)")
                .isFalse();

        long matchingDelayMs = (matchingTaskStartedAtNanos.get() - t0) / 1_000_000;

        releaseSaturating.countDown();
        assertThat(sharedTaskRan.await(5, TimeUnit.SECONDS))
                .as("점유를 풀어주면 공용 풀 작업도 결국은 실행된다")
                .isTrue();
        long sharedDelayMs = (sharedTaskStartedAtNanos.get() - t0) / 1_000_000;

        System.out.println("=====ASYNC_ISOLATION===== matchingExecutor 시작 지연=" + matchingDelayMs
                + "ms · 공용 taskExecutor 시작 지연(점유 해제까지)=" + sharedDelayMs + "ms =====ASYNC_ISOLATION=====");

        assertThat(matchingDelayMs).isLessThan(sharedDelayMs);
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
