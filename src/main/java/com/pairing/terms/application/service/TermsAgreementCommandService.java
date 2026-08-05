package com.pairing.terms.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.terms.application.command.AgreeTermsCommand;
import com.pairing.terms.application.usecase.TermsAgreementCommandUseCase;
import com.pairing.terms.application.usecase.TermsQueryUseCase;
import com.pairing.terms.domain.model.Terms;
import com.pairing.terms.domain.model.TermsAgreement;
import com.pairing.terms.domain.repository.TermsAgreementRepository;
import com.pairing.terms.exception.TermsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class TermsAgreementCommandService implements TermsAgreementCommandUseCase {

    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsQueryUseCase termsQueryUseCase;

    @Override
    public void agreeAll(Long accountId, String targetRole, List<AgreeTermsCommand> agreements, String userAgent) {
        List<Terms> latestTerms = termsQueryUseCase.findLatestByRole(targetRole);

        Set<Long> validIds = new HashSet<>(latestTerms.stream().map(Terms::getId).toList());
        Set<Long> agreedIds = new HashSet<>(agreements.stream()
                .filter(AgreeTermsCommand::agreed)
                .map(AgreeTermsCommand::termsId)
                .toList());

        // 프론트가 보낸 약관 ID가 현재 노출 중인 약관 목록에 없으면, 낡은 화면이거나 조작된 요청이다.
        boolean hasUnknown = agreements.stream()
                .anyMatch(agreement -> !validIds.contains(agreement.termsId()));
        if (hasUnknown) {
            throw new BusinessException(TermsErrorCode.UNKNOWN_TERMS_INCLUDED);
        }

        boolean allRequiredAgreed = latestTerms.stream()
                .filter(Terms::isRequired)
                .allMatch(terms -> agreedIds.contains(terms.getId()));
        if (!allRequiredAgreed) {
            throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        List<TermsAgreement> records = agreements.stream()
                .map(agreement -> TermsAgreement.create(
                        accountId, agreement.termsId(), agreement.agreed(), userAgent))
                .toList();

        termsAgreementRepository.saveAll(records);
    }
}
