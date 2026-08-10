package com.pairing.negotiation.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 헤더 배지용 응답 대기 협상 건수. 0 이면 배지를 숨긴다. */
@Schema(description = "응답 대기 협상 건수")
public record NegotiationWaitingCountResponse(

        @Schema(description = "내가 답해야 하는 협상 수. 0 이면 배지 숨김", example = "2") long waitingCount
) {
}
