package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.freelancer.application.result.FreelancerMyPageResult;
import com.pairing.freelancer.application.usecase.FreelancerQueryUseCase;
import com.pairing.freelancer.application.usecase.ResumeUseCase;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.freelancer.domain.model.ResumeStatus;
import com.pairing.global.exception.BusinessException;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FreelancerQueryService implements FreelancerQueryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final ReviewUseCase reviewUseCase;
    private final ResumeUseCase resumeUseCase;

    @Override
    public FreelancerMyPageResult findMyPage(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);
        FreelancerProfile profile = accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        ReviewSummaryResult reviewSummary = reviewUseCase.getSummary(accountId);
        boolean resumeCompleted = resumeUseCase.findMyResume(accountId)
                .map(resume -> resume.status() == ResumeStatus.COMPLETED)
                .orElse(false);

        return new FreelancerMyPageResult(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getPhone(),
                profile.getBirthDate(),
                profile.getAddress(),
                fileQueryUseCase.findObjectKey(profile.getProfileFileId()).orElse(null),
                profile.isAiMatchingAgreed(),
                FreelancerGrade.valueOf(profile.getGrade()),
                reviewSummary.averageScore(),
                reviewSummary.reviewCount(),
                resumeCompleted,
                // TODO: project/settlement 도메인 구현 후 진행 중 프로젝트·미납 요금 확인
                true
        );
    }
}
