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
import com.pairing.review.application.usecase.ReviewUseCase;
import com.pairing.review.domain.model.ReviewRating;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        ReviewRating rating = ratings(List.of(profile.getAccountId())).get(profile.getAccountId());
        return toResult(profile, rating);
    }

    @Override
    public Map<Long, FreelancerCandidateSummaryResult> getSummaries(List<Long> freelancerProfileIds) {
        if (freelancerProfileIds.isEmpty()) {
            return Map.of();
        }

        // 프로필을 먼저 모은다. 평점 조회에 accountId 가 필요해서 순서를 바꿀 수 없다.
        List<FreelancerProfile> profiles = freelancerProfileIds.stream()
                .distinct()
                .map(accountQueryUseCase::findFreelancerProfileById)
                .flatMap(Optional::stream)
                .toList();

        Map<Long, ReviewRating> ratings = ratings(profiles.stream().map(FreelancerProfile::getAccountId).toList());

        // 입력 순서를 지킨다. 매칭 후보는 점수 내림차순으로 넘어오는데 Map 이 순서를 흐트러뜨리면
        // 부르는 쪽이 다시 정렬해야 한다.
        Map<Long, FreelancerCandidateSummaryResult> summaries = new LinkedHashMap<>();
        for (FreelancerProfile profile : profiles) {
            summaries.put(profile.getId(), toResult(profile, ratings.get(profile.getAccountId())));
        }
        return summaries;
    }

    /** 받은 리뷰가 없는 계정은 집계 결과에 없다. 여기서 0건으로 채워 호출부가 null 을 안 보게 한다. */
    private Map<Long, ReviewRating> ratings(List<Long> accountIds) {
        Map<Long, ReviewRating> found = reviewUseCase.getRatings(accountIds);
        Map<Long, ReviewRating> filled = new LinkedHashMap<>();
        for (Long accountId : accountIds) {
            filled.put(accountId, found.getOrDefault(accountId, ReviewRating.empty(accountId)));
        }
        return filled;
    }

    private FreelancerCandidateSummaryResult toResult(FreelancerProfile profile, ReviewRating rating) {
        Account account = accountQueryUseCase.getById(profile.getAccountId());
        return new FreelancerCandidateSummaryResult(
                profile.getId(),
                profile.getAccountId(),
                account.getName(),
                fileQueryUseCase.findObjectKey(profile.getProfileFileId()).orElse(null),
                FreelancerGrade.of(profile.getGrade()),
                rating.averageScore(),
                rating.reviewCount()
        );
    }
}
