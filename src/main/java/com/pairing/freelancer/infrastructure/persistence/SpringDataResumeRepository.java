package com.pairing.freelancer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpringDataResumeRepository extends JpaRepository<ResumeJpaEntity, Long> {

    Optional<ResumeJpaEntity> findByAccountId(Long accountId);

    List<ResumeJpaEntity> findByAccountIdIn(Collection<Long> accountIds);

    @Query("SELECT r.accountId FROM ResumeJpaEntity r")
    List<Long> findAllAccountIds();
}
