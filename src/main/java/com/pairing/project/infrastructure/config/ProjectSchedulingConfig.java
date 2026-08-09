package com.pairing.project.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화.
 *
 * <p>프로젝트 모집 기간 만료 처리(정책 P46)에 필요해서 켠다. 스프링 부트는 기본으로 꺼져 있고,
 * 이 스위치는 애플리케이션 전역에 적용된다. 다른 도메인이 {@code @Scheduled} 를 붙이면 함께 동작한다.
 *
 * <p>인스턴스를 여러 대로 늘리면 같은 작업이 중복 실행된다. 지금은 단일 서버라 두지 않았고,
 * 늘릴 때는 분산 락이나 스케줄러 전용 인스턴스가 필요하다.
 */
@Configuration
@EnableScheduling
public class ProjectSchedulingConfig {
}
