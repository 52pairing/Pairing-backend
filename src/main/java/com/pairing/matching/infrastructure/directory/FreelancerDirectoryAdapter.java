package com.pairing.matching.infrastructure.directory;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;
import com.pairing.freelancer.application.result.ResumeResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import com.pairing.matching.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@link FreelancerDirectoryPort}의 실제 구현. account/freelancer 도메인의 인바운드 포트를 조합한다. */
@Component
@RequiredArgsConstructor
public class FreelancerDirectoryAdapter implements FreelancerDirectoryPort {

    private final FreelancerCandidateSummaryUseCase freelancerCandidateSummaryUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final FreelancerConditionUseCase freelancerConditionUseCase;
    private final ResumeUseCase resumeUseCase;

    @Override
    public Long resolveFreelancerId(Long accountId) {
        return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .map(FreelancerProfile::getId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
    }

    @Override
    public FreelancerCardSummary findCardSummary(Long freelancerId) {
        FreelancerCandidateSummaryResult summary = freelancerCandidateSummaryUseCase.getSummary(freelancerId);
        return new FreelancerCardSummary(summary.name(), summary.profileImageUrl(), summary.grade(),
                summary.ratingAverage(), summary.reviewCount());
    }

    @Override
    public FreelancerConditionResponse findCondition(Long freelancerId) {
        Long accountId = accountQueryUseCase.findFreelancerProfileById(freelancerId)
                .map(FreelancerProfile::getAccountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
        return freelancerConditionUseCase.findMyCondition(accountId)
                .map(FreelancerConditionResponse::from)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
    }

    @Override
    public FreelancerResumeSummary findResumeSummary(Long freelancerId) {
        Long accountId = accountQueryUseCase.findFreelancerProfileById(freelancerId)
                .map(FreelancerProfile::getAccountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));
        ResumeResult resume = resumeUseCase.findMyResume(accountId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FREELANCER_NOT_FOUND));

        List<FreelancerResumeSummary.CareerEntry> careers = resume.careers().stream()
                .map(career -> new FreelancerResumeSummary.CareerEntry(career.getCompanyName(),
                        career.getDepartmentRank(), career.getJobDescription()))
                .toList();
        return new FreelancerResumeSummary(resume.selfIntroduction(), careers);
    }
}
