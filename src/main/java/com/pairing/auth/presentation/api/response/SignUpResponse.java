package com.pairing.auth.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignUpResponse(

        @Schema(description = "생성된 계정 ID", example = "1")
        Long accountId,

        @Schema(description = "역할", example = "CLIENT")
        String role
) {
}
