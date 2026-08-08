package com.pairing.review.presentation.api.response;

import com.pairing.review.application.result.SiteReviewSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** 관리자 사이트 리뷰 관리 상단 요약과 별점 분포. (요구사항 R40) */
@Schema(description = "관리자 사이트 리뷰 요약")
public record SiteReviewSummaryResponse(

        @Schema(description = "평균 별점", example = "4.8") double ratingAverage,
        @Schema(description = "전체 리뷰 수", example = "4") long totalCount,
        @Schema(description = "이번 달 리뷰 수", example = "1") long thisMonthCount,
        @Schema(description = "홍보 활용 수", example = "2") long promotedCount,
        @Schema(description = "홍보 제외 수", example = "2") long notPromotedCount,
        @Schema(description = "공개 수", example = "3") long publicCount,

        // 키는 별점(1~5), 값은 건수. 화면의 막대 그래프에 그대로 쓴다.
        @Schema(description = "별점 분포", example = "{\"5\": 2, \"4\": 1, \"3\": 1, \"2\": 0, \"1\": 0}")
        Map<Integer, Long> scoreDistribution
) {

    public static SiteReviewSummaryResponse from(SiteReviewSummaryResult result) {
        return new SiteReviewSummaryResponse(result.ratingAverage(), result.totalCount(), result.thisMonthCount(),
                result.promotedCount(), result.notPromotedCount(), result.publicCount(),
                result.scoreDistribution());
    }
}
