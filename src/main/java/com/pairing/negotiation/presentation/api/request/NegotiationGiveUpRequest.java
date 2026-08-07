package com.pairing.negotiation.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 협상 포기. 누르는 즉시 협상 결렬로 종료된다. (요구사항 R10) */
@Schema(description = "협상 포기 요청")
public record NegotiationGiveUpRequest(

        @Schema(description = "포기 사유(선택)", example = "예산이 맞지 않습니다.")
        @Size(max = 255, message = "사유는 255자 이하여야 합니다.")
        String reason
) {
}
