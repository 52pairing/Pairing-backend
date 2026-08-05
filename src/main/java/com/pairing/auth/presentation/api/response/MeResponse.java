package com.pairing.auth.presentation.api.response;

import com.pairing.account.domain.model.Account;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인 사용자 응답")
public record MeResponse(

        @Schema(description = "계정 ID", example = "1")
        Long accountId,

        @Schema(description = "이메일", example = "user@pairing.com")
        String email,

        @Schema(description = "역할", example = "FREELANCER")
        String role,

        @Schema(description = "이름", example = "홍길동")
        String name,

        @Schema(description = "임시 비밀번호 상태", example = "false")
        boolean tempPassword
) {

    public static MeResponse from(Account account) {
        return new MeResponse(
                account.getId(),
                account.getEmail(),
                account.getRole().name(),
                account.getName(),
                account.isTempPassword()
        );
    }
}
