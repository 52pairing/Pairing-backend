package com.pairing.matching.application.result.admin;

import java.time.LocalDateTime;

public record AiLogItem(
        Long logId,
        String agentType,
        String refType,
        Long refId,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
}
