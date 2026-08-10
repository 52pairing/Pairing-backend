package com.pairing.support.application.result;

import java.time.LocalDateTime;

public record ChatbotAnswerResult(
        Long sessionId,
        String question,
        String answer,
        int remainingQuota,
        LocalDateTime createdAt
) {
}
