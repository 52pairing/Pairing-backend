package com.pairing.freelancer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataResumeDraftRepository extends JpaRepository<ResumeDraftJpaEntity, Long> {

    Optional<ResumeDraftJpaEntity> findByAccountId(Long accountId);

    void deleteByAccountId(Long accountId);
}
