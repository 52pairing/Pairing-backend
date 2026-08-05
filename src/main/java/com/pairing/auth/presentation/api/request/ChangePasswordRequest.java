package com.pairing.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 변경 요청")
public record ChangePasswordRequest(

        @Schema(description = "현재 비밀번호(임시 비밀번호 포함)")
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @Schema(description = "새 비밀번호(대소문자+숫자+특수문자, 8~20자)")
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        String newPassword,

        @Schema(description = "새 비밀번호 확인")
        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        String newPasswordConfirm
) {
}
