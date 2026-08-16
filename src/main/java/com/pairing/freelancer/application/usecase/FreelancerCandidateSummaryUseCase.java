package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;

import java.util.List;
import java.util.Map;

/** 매칭 도메인이 후보 카드 요약을 조회하는 인바운드 포트. */
public interface FreelancerCandidateSummaryUseCase {

    /** freelancer_profile.id 로 조회한다. 없으면 {@code FR_005}. */
    FreelancerCandidateSummaryResult getSummary(Long freelancerProfileId);

    /**
     * freelancer_profile.id 목록으로 일괄 조회. 없는 id 는 결과에서 빠진다.
     *
     * <p>후보 목록이 카드마다 {@link #getSummary} 를 부르면 N+1 이라 만들었다. 한 명이라도
     * 사라졌다고 목록 전체가 막히면 안 되므로, 단건과 달리 <b>없는 id 를 예외로 보지 않는다.</b>
     * 부르는 쪽이 결과에 없는 id 를 걸러 쓴다.
     */
    Map<Long, FreelancerCandidateSummaryResult> getSummaries(List<Long> freelancerProfileIds);
}
