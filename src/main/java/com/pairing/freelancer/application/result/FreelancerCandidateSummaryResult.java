package com.pairing.freelancer.application.result;

import com.pairing.freelancer.domain.model.FreelancerGrade;

/**
 * 매칭 도메인이 후보 카드에 쓰는 프리랜서 요약. (freelancer_profile.id 기준 조회)
 *
 * <p>받은 리뷰가 없으면 {@code ratingAverage} 는 null 이다.
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
