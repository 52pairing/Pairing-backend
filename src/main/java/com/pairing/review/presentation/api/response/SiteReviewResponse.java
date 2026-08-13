package com.pairing.review.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.application.result.SiteReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 사이트 이용 후기. 비로그인 메인에는 관리자가 홍보로 켠 4점 이상만 노출한다. */
@Schema(description = "사이트 리뷰 응답")
public record SiteReviewResponse(

        @Schema(description = "사이트 리뷰 ID", example = "950") Long siteReviewId,
        @Schema(description = "작성자 구분") PartyRole writerRole,
        @Schema(description = "작성자 표시명. 메인 노출 시 마스킹된다.", example = "고**") String writerName,
        @Schema(description = "별점", example = "5") int score,
        @Schema(description = "내용") String content,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "홍보 활용 여부. 이 API 응답에서는 항상 true", example = "true") boolean promoted,
        @Schema(description = "작성 시각") LocalDateTime createdAt
) {

    public static SiteReviewResponse from(SiteReviewResult result) {
        return new SiteReviewResponse(result.siteReviewId(), result.writerRole(), result.writerName(),
                result.score(), result.content(), result.projectTitle(), result.promoted(),
                result.createdAt());
    }
}
