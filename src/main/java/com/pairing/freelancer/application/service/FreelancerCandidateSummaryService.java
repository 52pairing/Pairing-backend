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
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FreelancerCandidateSummaryService implements FreelancerCandidateSummaryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final ReviewUseCase reviewUseCase;

    @Override
    public FreelancerCandidateSummaryResult getSummary(Long freelancerProfileId) {
        FreelancerProfile profile = accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .orElseThrow(() -> new BusinessException(FreelancerErrorCode.CANDIDATE_NOT_FOUND));
        Account account = accountQueryUseCase.getById(profile.getAccountId());
        ReviewSummaryResult reviewSummary = reviewUseCase.getSummary(profile.getAccountId());

        return new FreelancerCandidateSummaryResult(
                profile.getId(),
                profile.getAccountId(),
                account.getName(),
                fileQueryUseCase.findObjectKey(profile.getProfileFileId()).orElse(null),
                FreelancerGrade.of(profile.getGrade()),
                reviewSummary.averageScore(),
                reviewSummary.reviewCount()
        );
    }
}
