package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FreelancerCandidateSummaryService implements FreelancerCandidateSummaryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;

    @Override
    public FreelancerCandidateSummaryResult getSummary(Long freelancerProfileId) {
        FreelancerProfile profile = accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .orElseThrow(() -> new BusinessException(FreelancerErrorCode.CANDIDATE_NOT_FOUND));
        Account account = accountQueryUseCase.getById(profile.getAccountId());

        return new FreelancerCandidateSummaryResult(
                profile.getId(),
                profile.getAccountId(),
                account.getName(),
                fileQueryUseCase.findObjectKey(profile.getProfileFileId()).orElse(null),
                FreelancerGrade.valueOf(profile.getGrade()),
                // TODO: review 도메인 구현 후 실제 평균/건수로 교체
                null,
                0
        );
    }
}
