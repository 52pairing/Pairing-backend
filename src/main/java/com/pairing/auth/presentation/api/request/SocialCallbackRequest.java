package com.pairing.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "소셜 로그인 콜백 요청")
public record SocialCallbackRequest(

        @Schema(description = "공급자가 준 인가 코드")
        @NotBlank(message = "인가 코드는 필수입니다.")
        String code,

        @Schema(description = "인가 요청 시 발급받은 state")
        @NotBlank(message = "state는 필수입니다.")
        String state
) {
}
