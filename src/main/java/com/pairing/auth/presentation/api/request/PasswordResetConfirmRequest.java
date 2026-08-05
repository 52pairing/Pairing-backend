package com.pairing.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "재설정 링크 확인 요청")
public record PasswordResetConfirmRequest(

        @Schema(description = "메일 링크에 담긴 토큰")
        @NotBlank(message = "토큰은 필수입니다.")
        String token
) {
}
