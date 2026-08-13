package com.pairing.matching.presentation.api.response.admin;

import com.pairing.matching.application.result.admin.AiLogItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "AI 로그")
public record AiLogResponse(
        Long logId,
        String agentType,
        String refType,
        Long refId,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {

    public static AiLogResponse from(AiLogItem item) {
        return new AiLogResponse(item.logId(), item.agentType(), item.refType(), item.refId(),
                item.status(), item.errorMessage(), item.createdAt());
    }
}
