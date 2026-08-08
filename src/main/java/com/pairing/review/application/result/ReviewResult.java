package com.pairing.review.application.result;

import com.pairing.meta.domain.model.PartyRole;

import java.time.LocalDateTime;

public record ReviewResult(
        Long reviewId,
        Long contractId,
        String projectTitle,
        String reviewerName,
        PartyRole reviewerRole,
        int score,
        String content,
        LocalDateTime createdAt
) {
}
