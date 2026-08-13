package com.pairing.matching.application.result.admin;

import java.time.LocalDateTime;

public record EmbeddingMissingItem(
        String targetType,
        Long targetId,
        String displayName,
        String status,
        String reason,
        String model,
        String lastLogStatus,
        LocalDateTime lastLogAt
) {
}
