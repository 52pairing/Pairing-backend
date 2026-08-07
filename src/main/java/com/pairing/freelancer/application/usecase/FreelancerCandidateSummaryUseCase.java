package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;

/** 매칭 도메인이 후보 카드 요약을 조회하는 인바운드 포트. */
public interface FreelancerCandidateSummaryUseCase {

    /** freelancer_profile.id 로 조회한다. 없으면 {@code FR_005}. */
    FreelancerCandidateSummaryResult getSummary(Long freelancerProfileId);
}
