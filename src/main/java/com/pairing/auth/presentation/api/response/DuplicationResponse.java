package com.pairing.auth.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "중복 확인 응답")
public record DuplicationResponse(

        @Schema(description = "이미 사용 중이면 true", example = "false")
        boolean duplicated
) {
}
