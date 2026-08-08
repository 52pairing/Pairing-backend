package com.pairing.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataFreelancerProfileRepository extends JpaRepository<FreelancerProfileJpaEntity, Long> {

    Optional<FreelancerProfileJpaEntity> findByAccountIdAndDeletedAtIsNull(Long accountId);

    Optional<FreelancerProfileJpaEntity> findByIdAndDeletedAtIsNull(Long id);
}
