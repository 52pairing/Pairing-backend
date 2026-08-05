package com.pairing.terms.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataTermsAgreementRepository extends JpaRepository<TermsAgreementJpaEntity, Long> {

    List<TermsAgreementJpaEntity> findAllByAccountId(Long accountId);
}
