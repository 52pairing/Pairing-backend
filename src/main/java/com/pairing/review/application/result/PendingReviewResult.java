package com.pairing.review.application.result;

import java.time.LocalDateTime;

/**
 * 아직 리뷰를 쓰지 않은 계약 한 건.
 *
 * <p>작성된 리뷰({@link ReviewResult})와 모양이 다르다. 별점·내용·작성시각이 아직 없고,
 * 화면은 "어느 계약에 누구를 평가하러 가는지"만 있으면 된다.
 */
public record PendingReviewResult(
        Long contractId,
        String projectTitle,
        String counterpartName,
        LocalDateTime completedAt
) {
}
