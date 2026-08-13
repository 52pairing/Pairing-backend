package com.pairing.matching.application.result.admin;

public record EmbeddingMissingSummary(
        long freelancerMissingCount,
        long positionMissingCount
) {
}
