package com.pairing.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataClientProfileRepository extends JpaRepository<ClientProfileJpaEntity, Long> {

    Optional<ClientProfileJpaEntity> findByAccountIdAndDeletedAtIsNull(Long accountId);

    boolean existsByBusinessNo(String businessNo);
}
