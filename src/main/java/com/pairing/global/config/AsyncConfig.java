package com.pairing.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // 비동기 기능 활성화
public class AsyncConfig {

    /**
     * 스프링 부트의 기본 비동기 Executor로 등록하기 위해 빈 이름을 "taskExecutor"로 지정한다.
     * 이름을 명시하지 않고 @Async 만 붙여도 이 풀을 자동으로 사용한다.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);    // 기본 스레드 수
        executor.setMaxPoolSize(10);    // 최대 스레드 수
        executor.setQueueCapacity(500); // 큐 대기 용량
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
