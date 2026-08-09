package com.pairing.support.application.result;

/** 관리자 1:1 문의 목록 상단 요약 카드. */
public record InquirySummaryResult(
        long totalCount,
        long pendingCount,
        long answeredCount,
        long todayCount
) {
}
