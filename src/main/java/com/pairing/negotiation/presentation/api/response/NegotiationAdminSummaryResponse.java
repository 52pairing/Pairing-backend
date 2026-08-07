package com.pairing.negotiation.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 AI Agent 관리 상단 요약 카드. */
@Schema(description = "관리자 협상 요약")
public record NegotiationAdminSummaryResponse(

        @Schema(description = "전체 협상 세션", example = "3") long totalCount,
        @Schema(description = "진행 중", example = "1") long inProgressCount,
        @Schema(description = "협상 완료", example = "1") long agreedCount,
        @Schema(description = "협상 결렬", example = "1") long failedCount,
        @Schema(description = "평균 협상 횟수", example = "4.0") double averageRoundCount,
        @Schema(description = "평균 소요 일수", example = "2.3") double averageDurationDays
) {
}
