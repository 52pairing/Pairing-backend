package com.pairing.review.application.command;

/**
 * {@code contractId} 로 프로젝트·상대를 유도해야 하지만 contract 도메인이 아직 스켈레톤이라
 * {@code projectId}/{@code revieweeAccountId} 를 요청에서 직접 받는다. 도메인이 갖춰지면 제거한다.
 */
public record CreateReviewCommand(
        Long contractId,
        Long projectId,
        Long reviewerAccountId,
        Long revieweeAccountId,
        int counterpartScore,
        String counterpartContent,
        int siteScore,
        String siteContent
) {
}
