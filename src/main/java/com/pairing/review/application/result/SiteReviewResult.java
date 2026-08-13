package com.pairing.review.application.result;

import com.pairing.meta.domain.model.PartyRole;

import java.time.LocalDateTime;

public record SiteReviewResult(
        Long siteReviewId,
        PartyRole writerRole,
        String writerName,
        int score,
        String content,
        String projectTitle,
        boolean promoted,
        LocalDateTime createdAt
) {
}
