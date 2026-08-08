package com.pairing.negotiation.application.result.admin;

/**
 * 관리자 협상 요약 집계. AI Agent 관리 화면 상단 카드.
 *
 * @param total            전체 협상 세션 수
 * @param inProgress       진행 중
 * @param agreed           타결
 * @param failed           결렬
 * @param averageRound     평균 라운드 수(협상 없으면 0)
 * @param averageDurationDays 평균 소요 일수(종료된 협상 기준, 없으면 0)
 */
public record AdminSummary(
        long total,
        long inProgress,
        long agreed,
        long failed,
        double averageRound,
        double averageDurationDays
) {
}
