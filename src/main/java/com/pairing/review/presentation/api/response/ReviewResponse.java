package com.pairing.review.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.application.result.ReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 상호 평가 1건. */
@Schema(description = "리뷰 응답")
public record ReviewResponse(

        @Schema(description = "리뷰 ID", example = "900") Long reviewId,
        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "작성자 이름", example = "주식회사 페어링") String reviewerName,
        @Schema(description = "작성자 구분") PartyRole reviewerRole,
        @Schema(description = "별점", example = "5") int score,
        @Schema(description = "리뷰 내용") String content,
        @Schema(description = "작성 시각") LocalDateTime createdAt
) {

    public static ReviewResponse from(ReviewResult result) {
        return new ReviewResponse(result.reviewId(), result.contractId(), result.projectTitle(),
                result.reviewerName(), result.reviewerRole(), result.score(), result.content(),
                result.createdAt());
    }
}
