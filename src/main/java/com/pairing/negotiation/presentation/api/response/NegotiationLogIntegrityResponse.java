package com.pairing.negotiation.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 협상 로그 위변조 검증 결과. 분쟁 시 "로그가 조작되지 않았음"을 증명한다. */
@Schema(description = "협상 로그 무결성 검증 결과")
public record NegotiationLogIntegrityResponse(

        @Schema(description = "무결성 유지 여부", example = "true") boolean valid,
        @Schema(description = "무결성이 깨진 첫 로그 ID(정상이면 null)", example = "null") Long brokenAtMessageId,
        @Schema(description = "검증한 로그 수", example = "7") int checked
) {
}
