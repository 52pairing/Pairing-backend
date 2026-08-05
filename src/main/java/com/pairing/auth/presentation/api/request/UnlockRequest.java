package com.pairing.auth.presentation.api.request;

import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.command.UnlockCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "계정 잠금 해제 요청")
public record UnlockRequest(

        @Schema(description = "아이디(이메일)", example = "user@pairing.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "역할", example = "CLIENT")
        @NotNull(message = "역할은 필수입니다.")
        Role role,

        @Schema(description = "UNLOCK 용도로 받은 6자리 인증코드", example = "123456")
        @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자입니다.")
        String code
) {

    public UnlockCommand toCommand() {
        return new UnlockCommand(email, role, code);
    }
}
