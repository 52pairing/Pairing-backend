package com.pairing.freelancer.application.result;

import com.pairing.freelancer.domain.model.FreelancerGrade;

/**
 * 매칭 도메인이 후보 카드에 쓰는 프리랜서 요약. (freelancer_profile.id 기준 조회)
 *
 * <p>{@code ratingAverage}/{@code reviewCount} 는 review 도메인이 아직 없어 각각 null / 0 으로 내려간다.
 */
public record FreelancerCandidateSummaryResult(
        Long freelancerProfileId,
        Long accountId,
        String name,
        String profileImageUrl,
        FreelancerGrade grade,
        Double ratingAverage,
        int reviewCount
) {
}
