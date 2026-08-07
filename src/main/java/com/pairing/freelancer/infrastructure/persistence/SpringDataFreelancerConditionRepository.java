package com.pairing.freelancer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataFreelancerConditionRepository extends JpaRepository<FreelancerConditionJpaEntity, Long> {

    Optional<FreelancerConditionJpaEntity> findByAccountId(Long accountId);
}
