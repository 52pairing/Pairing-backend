package com.pairing.freelancer.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 포트폴리오 등록/수정. (마이페이지 &gt; 포트폴리오)
 *
 * <p>이력서에 붙이는 대표 포트폴리오 파일과 별개로, 작업물을 여러 건 관리하는 메뉴다.
 * 파일과 링크 중 최소 하나는 있어야 한다. 검증은 서비스에서 한다.
 */
@Schema(description = "포트폴리오 요청")
public record PortfolioRequest(

        @Schema(description = "제목", example = "쇼핑몰 관리자 페이지 리뉴얼")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "설명", example = "React 기반 관리자 페이지를 전면 리뉴얼했습니다.")
        @Size(max = 1000, message = "설명은 1000자를 넘을 수 없습니다.")
        String description,

        @Schema(description = "첨부 파일 ID. 파일 API 로 먼저 업로드한다.", example = "42")
        Long fileId,

        @Schema(description = "외부 링크", example = "https://github.com/pairing/sample")
        @Size(max = 500, message = "링크는 500자를 넘을 수 없습니다.")
        String linkUrl
) {
}
