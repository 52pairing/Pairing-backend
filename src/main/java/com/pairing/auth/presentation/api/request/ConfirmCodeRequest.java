package com.pairing.auth.presentation.api.request;

import com.pairing.auth.domain.model.VerificationPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "이메일 인증코드 확인 요청")
public record ConfirmCodeRequest(

        @Schema(description = "인증 대상 이메일", example = "user@pairing.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "인증 용도", example = "SIGNUP")
        @NotNull(message = "인증 용도는 필수입니다.")
        VerificationPurpose purpose,

        @Schema(description = "6자리 인증코드", example = "123456")
        @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자입니다.")
        String code
) {
}
