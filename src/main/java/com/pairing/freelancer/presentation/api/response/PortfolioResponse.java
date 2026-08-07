package com.pairing.freelancer.presentation.api.response;

import com.pairing.global.infrastructure.s3.CdnMappable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 포트폴리오 한 건. */
@Schema(description = "포트폴리오")
public record PortfolioResponse(

        @Schema(description = "포트폴리오 ID", example = "70") Long portfolioId,
        @Schema(description = "제목", example = "쇼핑몰 관리자 페이지 리뉴얼") String title,
        @Schema(description = "설명") String description,
        @Schema(description = "파일 ID", example = "42") Long fileId,
        @Schema(description = "파일 URL. 응답 시점에 CDN 절대 URL 로 변환된다") String fileUrl,
        @Schema(description = "외부 링크") String linkUrl,
        @Schema(description = "정렬 순서", example = "0") int sortOrder,
        @Schema(description = "등록 시각") LocalDateTime createdAt
) implements CdnMappable {
}
