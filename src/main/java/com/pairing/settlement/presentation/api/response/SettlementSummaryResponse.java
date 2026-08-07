package com.pairing.settlement.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 정산 대시보드 집계. (요구사항 R39) */
@Schema(description = "정산 집계 응답")
public record SettlementSummaryResponse(

        @Schema(description = "총 수수료 수익(원)", example = "125000000") Long totalRevenue,
        @Schema(description = "이번 달 수익(원)", example = "8400000") Long monthlyRevenue,
        @Schema(description = "결제 예정 금액(원)", example = "3200000") Long scheduledAmount,
        @Schema(description = "미납 금액(원)", example = "450000") Long overdueAmount,
        @Schema(description = "결제 실패 금액(원)", example = "0") Long failedAmount,
        @Schema(description = "위약금 수수료(원)", example = "1000000") Long penaltyRevenue
) {
}
