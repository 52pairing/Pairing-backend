package com.pairing.negotiation.infrastructure.project;

import org.springframework.data.jpa.repository.JpaRepository;

/** project 읽기 전용 스프링 데이터 리포지토리. 조회만 사용한다. */
public interface SpringDataProjectReadRepository extends JpaRepository<ProjectReadJpaEntity, Long> {
}
