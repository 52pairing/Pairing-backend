package com.pairing.freelancer.presentation.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 임시 저장된 이력서 초안. {@code payload} 는 저장할 때 보낸 JSON 그대로다. */
@Schema(description = "이력서 임시 저장 응답")
public record ResumeDraftResponse(

        @Schema(description = "저장했던 화면 입력값 전체") JsonNode payload,
        @Schema(description = "마지막 임시 저장 시각") LocalDateTime savedAt
) {
}
