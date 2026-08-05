package com.pairing.auth.presentation.api.request;

import com.pairing.account.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "이메일 로그인 요청")
public record LoginRequest(

        @Schema(description = "로그인 아이디(이메일)", example = "user@pairing.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "비밀번호", example = "Passw0rd!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @Schema(description = "로그인 탭에서 고른 역할. 같은 이메일이라도 역할이 다르면 다른 계정이다.",
                example = "CLIENT")
        @NotNull(message = "역할은 필수입니다.")
        Role role
) {
}
