package com.pairing.support.application.result;

import java.time.LocalDate;

public record ChatbotQuotaResult(
        LocalDate quotaDate,
        int dailyLimit,
        int usedCount,
        int remainingCount
) {
}
