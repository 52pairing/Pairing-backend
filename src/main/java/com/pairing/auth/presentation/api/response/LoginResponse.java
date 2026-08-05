package com.pairing.auth.presentation.api.response;

import com.pairing.auth.application.result.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** 토큰은 HttpOnly 쿠키로 나가므로 본문에 담지 않는다. */
@Schema(description = "로그인 응답")
public record LoginResponse(

        @Schema(description = "계정 ID", example = "1")
        Long accountId,

        @Schema(description = "역할", example = "FREELANCER")
        String role,

        @Schema(description = "이름", example = "홍길동")
        String name,

        @Schema(description = "임시 비밀번호 상태. true면 비밀번호 변경 화면으로 보내야 한다.", example = "false")
        boolean tempPassword
) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accountId(), result.role(), result.name(), result.tempPassword());
    }
}
