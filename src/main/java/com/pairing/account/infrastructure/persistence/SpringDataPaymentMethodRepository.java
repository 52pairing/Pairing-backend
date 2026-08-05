package com.pairing.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataPaymentMethodRepository extends JpaRepository<PaymentMethodJpaEntity, Long> {

    List<PaymentMethodJpaEntity> findAllByAccountIdAndDeletedAtIsNull(Long accountId);
}
