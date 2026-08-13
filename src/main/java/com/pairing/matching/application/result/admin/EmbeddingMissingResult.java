package com.pairing.matching.application.result.admin;

import org.springframework.data.domain.Page;

public record EmbeddingMissingResult(
        EmbeddingMissingSummary summary,
        Page<EmbeddingMissingItem> page
) {
}
