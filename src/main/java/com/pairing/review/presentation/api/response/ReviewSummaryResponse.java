package com.pairing.review.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 마이페이지의 평균 별점·건수. */
@Schema(description = "리뷰 요약 응답")
public record ReviewSummaryResponse(

        @Schema(description = "평균 별점", example = "4.5") Double averageScore,
        @Schema(description = "리뷰 건수", example = "12") int reviewCount,
        @Schema(description = "등급. 평균 별점과 건수로 산정된다.", example = "SENIOR") String grade
) {
}
