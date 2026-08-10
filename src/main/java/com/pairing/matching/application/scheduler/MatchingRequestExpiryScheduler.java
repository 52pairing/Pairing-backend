package com.pairing.matching.application.scheduler;

import com.pairing.matching.application.usecase.MatchingRequestCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매칭 요청 응답 기한(3일) 자동 만료. (정책 P45)
 *
 * <p>프리랜서가 응답하지 않고 방치하면 {@code REQUEST_PENDING}으로 영원히 남는다. 이 상태는
 * 무료 재추천 조건(P41, {@code MatchingRerecommendService.assertFreeAvailable}) 판단에도
 * "아직 진행 중"으로 잡혀서, 자동 만료가 안 되면 프리랜서 한 명이 방치하는 것만으로 클라이언트가
 * 무료 재추천을 영원히 못 쓰게 된다.
 *
 * <p>스케줄링 활성화({@code @EnableScheduling})는 {@code ProjectSchedulingConfig}가 전역으로 켜둔다.
 * 실제 배치 로직은 {@code MatchingRequestService.expireOverdueRequests()}에 있다 — 여기는 얇은 진입점.
 */
@Component
@RequiredArgsConstructor
public class MatchingRequestExpiryScheduler {

    private final MatchingRequestCommandUseCase matchingRequestCommandUseCase;

    /** 기본은 10분마다. 재추천이 막히는 창구를 짧게 유지하려고 프로젝트 모집 만료(매일 1회)보다 자주 돈다. */
    @Scheduled(cron = "${matching.request-expiry.cron:0 */10 * * * *}")
    public void expireOverdueRequests() {
        matchingRequestCommandUseCase.expireOverdueRequests();
    }
}
