package com.pairing.auth.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "인증코드 발송 응답")
public record SendCodeResponse(

        @Schema(description = "코드 만료 시각. 프론트는 이 값으로 타이머를 표시한다.")
        LocalDateTime expiresAt,

        @Schema(description = "이번 집계 구간에서 남은 발송 가능 횟수", example = "14")
        int remainingSendCount
) {
}
