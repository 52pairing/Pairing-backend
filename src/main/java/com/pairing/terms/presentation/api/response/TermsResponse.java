package com.pairing.terms.presentation.api.response;

import com.pairing.terms.domain.model.TermsType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 약관 한 건.
 *
 * <p>가입 화면은 {@code GET /api/v1/terms} 로 동의 항목 3개만 받고,
 * 푸터의 문서 링크는 {@code GET /api/v1/terms/documents} 로 개인정보 처리방침까지 함께 받는다.
 */
@Schema(description = "약관 응답")
public record TermsResponse(

        @Schema(description = "약관 ID. 가입 요청의 agreements[].termsId 에 그대로 넣는다.", example = "1")
        Long termsId,

        @Schema(description = "약관 코드", example = "SERVICE")
        String code,

        @Schema(description = "문서 성격. AGREEMENT 만 동의 대상이다.", example = "AGREEMENT")
        TermsType type,

        @Schema(description = "약관 제목", example = "서비스 이용약관 동의")
        String title,

        @Schema(description = "약관 버전", example = "v1.0")
        String version,

        @Schema(description = "필수 동의 여부. type 이 POLICY 면 의미 없다.", example = "true")
        boolean required,

        @Schema(description = "시행일")
        LocalDateTime effectiveAt,

        @Schema(description = "약관 전문")
        String content
) {
}
