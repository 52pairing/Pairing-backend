package com.pairing.terms.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 응답")
public record TermsResponse(

        @Schema(description = "약관 ID. 동의 요청 시 이 값을 그대로 보낸다.", example = "1")
        Long termsId,

        @Schema(description = "약관 코드", example = "SERVICE_CLIENT")
        String code,

        @Schema(description = "약관 제목", example = "클라이언트 서비스 이용약관")
        String title,

        @Schema(description = "약관 버전", example = "v1.0")
        String version,

        @Schema(description = "필수 동의 여부", example = "true")
        boolean required,

        @Schema(description = "약관 전문")
        String content
) {
}
