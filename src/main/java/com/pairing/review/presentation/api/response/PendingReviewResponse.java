package com.pairing.review.presentation.api.response;

import com.pairing.review.application.result.PendingReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 리뷰 작성 대기 한 건.
 *
 * <p>작성된 리뷰와 모양이 다르다. 별점·내용이 아직 없고, 화면은 "어느 계약에 누구를 평가하러 가는지"만
 * 있으면 된다. {@code contractId} 를 그대로 리뷰 작성 요청에 넣으면 된다.
 */
@Schema(description = "리뷰 작성 대기 항목")
public record PendingReviewResponse(

        @Schema(description = "계약 ID. 리뷰 작성 요청에 그대로 넣는다.", example = "600") Long contractId,
        @Schema(description = "프로젝트명", example = "AI 추천 엔진 개발") String projectTitle,
        @Schema(description = "평가할 상대. 클라이언트가 보면 프리랜서명, 프리랜서가 보면 기업명",
                example = "카카오") String counterpartName,
        @Schema(description = "계약 이행 완료 시각") LocalDateTime completedAt
) {

    public static PendingReviewResponse from(PendingReviewResult result) {
        return new PendingReviewResponse(result.contractId(), result.projectTitle(), result.counterpartName(),
                result.completedAt());
    }
}
