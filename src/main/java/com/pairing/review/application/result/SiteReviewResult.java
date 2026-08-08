package com.pairing.review.application.result;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReviewVisibility;

import java.time.LocalDateTime;

public record SiteReviewResult(
        Long siteReviewId,
        PartyRole writerRole,
        String writerName,
        int score,
        String content,
        String projectTitle,
        SiteReviewVisibility visibility,
        boolean promoted,
        LocalDateTime createdAt
) {
}
