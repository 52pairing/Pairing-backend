package com.pairing.freelancer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataResumeRepository extends JpaRepository<ResumeJpaEntity, Long> {

    Optional<ResumeJpaEntity> findByAccountId(Long accountId);
}
