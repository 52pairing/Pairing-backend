package com.pairing.terms.domain.repository;

import com.pairing.terms.domain.model.TermsAgreement;

import java.util.List;

public interface TermsAgreementRepository {

    List<TermsAgreement> saveAll(List<TermsAgreement> agreements);

    List<TermsAgreement> findAllByAccountId(Long accountId);
}
