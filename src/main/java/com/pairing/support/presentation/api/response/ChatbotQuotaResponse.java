package com.pairing.support.presentation.api.response;

import com.pairing.support.application.result.ChatbotQuotaResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 챗봇 사용 한도. 화면에서 남은 횟수를 미리 보여줄 때 쓴다. */
@Schema(description = "챗봇 한도 응답")
public record ChatbotQuotaResponse(

        @Schema(description = "기준 날짜") LocalDate quotaDate,
        @Schema(description = "하루 한도", example = "10") int dailyLimit,
        @Schema(description = "사용 횟수", example = "1") int usedCount,
        @Schema(description = "남은 횟수", example = "9") int remainingCount
) {

    public static ChatbotQuotaResponse from(ChatbotQuotaResult result) {
        return new ChatbotQuotaResponse(result.quotaDate(), result.dailyLimit(), result.usedCount(),
                result.remainingCount());
    }
}
