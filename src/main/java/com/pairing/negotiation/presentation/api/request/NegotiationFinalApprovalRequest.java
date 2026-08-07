package com.pairing.negotiation.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 최종 승인. (요구사항 R30)
 *
 * <p>15회 안에 합의되지 않으면 양측이 마지막 조건을 보고 직접 승인/거부한다.
 * 양측 모두 승인해야 계약 단계로 넘어가고, 한쪽이라도 거부하면 결렬된다.
 */
@Schema(description = "최종 승인 요청")
public record NegotiationFinalApprovalRequest(

        @Schema(description = "승인 여부", example = "true")
        @NotNull(message = "승인 여부는 필수입니다.")
        Boolean approved
) {
}
