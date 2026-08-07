package com.pairing.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 마이페이지 비밀번호 변경.
 *
 * <p>현재 비밀번호를 받지 않는다. 대신 이 요청 전에 이메일 인증코드를
 * {@code purpose=PASSWORD_CHANGE} 로 확인해 두어야 한다. 인증을 건너뛰면 {@code AU_006} 이다.
 */
@Schema(description = "비밀번호 변경 요청")
public record ChangePasswordRequest(

        @Schema(description = "새 비밀번호(대소문자+숫자+특수문자, 8~20자)", example = "Pairing!2026")
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        String newPassword,

        @Schema(description = "새 비밀번호 확인", example = "Pairing!2026")
        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        String newPasswordConfirm
) {
}
