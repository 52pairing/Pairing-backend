package com.pairing.settlement.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.PenaltyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 계약 파기 위약금. (요구사항 R30)
 *
 * <p>파기 주체가 상대방에게 10%, 플랫폼에 10%를 부담한다. 그래서 파기 1건에 위약금 2행이 생긴다.
 */
@Schema(description = "위약금 응답")
public record PenaltyResponse(

        @Schema(description = "위약금 ID", example = "800") Long penaltyId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "파기 주체") PartyRole terminatorRole,
        @Schema(description = "수령 대상", example = "PLATFORM", allowableValues = {"PLATFORM", "COUNTERPART"})
        String payeeType,
        @Schema(description = "수행분 보수(원). 위약금 계산 기준", example = "10000000") Long workedAmount,
        @Schema(description = "위약금 비율(%)", example = "10.00") BigDecimal penaltyRate,
        @Schema(description = "위약금(원)", example = "1000000") Long penaltyAmount,
        @Schema(description = "상태") PenaltyStatus status,
        @Schema(description = "납부 시각") LocalDateTime paidAt
) {
}
