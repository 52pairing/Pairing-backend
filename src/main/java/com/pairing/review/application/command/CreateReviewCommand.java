package com.pairing.review.application.command;

/**
 * 리뷰 작성. 프로젝트와 상대방은 {@code contractId} 로 계약에서 유도한다.
 *
 * <p>요청에서 직접 받지 않는다. 프론트가 보낸 값을 믿으면 남의 계약에 리뷰를 남기거나
 * 실제 계약과 다른 상대에게 평점이 쌓일 수 있다.
 */
public record CreateReviewCommand(
        Long contractId,
        Long reviewerAccountId,
        int counterpartScore,
        String counterpartContent,
        int siteScore,
        String siteContent
) {
}
