package com.pairing.auth.presentation.api.request;

import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.command.PasswordResetRequestCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "비밀번호 재설정 링크 요청")
public record PasswordResetRequest(

        @Schema(description = "아이디(이메일)", example = "user@pairing.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "역할. 같은 이메일이라도 역할이 다르면 다른 계정이다.", example = "FREELANCER")
        @NotNull(message = "역할은 필수입니다.")
        Role role,

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone
) {

    public PasswordResetRequestCommand toCommand() {
        return new PasswordResetRequestCommand(email, role, name, phone);
    }
}
