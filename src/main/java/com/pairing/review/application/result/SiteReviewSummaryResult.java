package com.pairing.review.application.result;

import java.util.Map;

public record SiteReviewSummaryResult(
        double ratingAverage,
        long totalCount,
        long thisMonthCount,
        long promotedCount,
        long notPromotedCount,
        long publicCount,
        Map<Integer, Long> scoreDistribution
) {
}
