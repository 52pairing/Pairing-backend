package com.pairing.terms.infrastructure.persistence;

import com.pairing.terms.domain.model.TermsAgreement;
import com.pairing.terms.domain.repository.TermsAgreementRepository;
import com.pairing.terms.infrastructure.mapper.TermsAgreementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TermsAgreementRepositoryAdapter implements TermsAgreementRepository {

    private final SpringDataTermsAgreementRepository springDataRepository;
    private final TermsAgreementMapper termsAgreementMapper;

    @Override
    public List<TermsAgreement> saveAll(List<TermsAgreement> agreements) {
        List<TermsAgreementJpaEntity> entities = agreements.stream()
                .map(termsAgreementMapper::toJpaEntity)
                .toList();

        return springDataRepository.saveAll(entities).stream()
                .map(termsAgreementMapper::toDomain)
                .toList();
    }

    @Override
    public List<TermsAgreement> findAllByAccountId(Long accountId) {
        return springDataRepository.findAllByAccountId(accountId).stream()
                .map(termsAgreementMapper::toDomain)
                .toList();
    }
}
