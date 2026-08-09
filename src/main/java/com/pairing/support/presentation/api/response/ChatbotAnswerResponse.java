package com.pairing.support.presentation.api.response;

import com.pairing.support.application.result.ChatbotAnswerResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 챗봇 응답. */
@Schema(description = "챗봇 응답")
public record ChatbotAnswerResponse(

        @Schema(description = "세션 ID. 다음 질문에 그대로 넣는다.", example = "1200") Long sessionId,
        @Schema(description = "질문", example = "착수금 수수료는 언제 결제하나요?") String question,
        @Schema(description = "답변") String answer,
        @Schema(description = "오늘 남은 질의 횟수", example = "9") int remainingQuota,
        @Schema(description = "응답 시각") LocalDateTime createdAt
) {

    public static ChatbotAnswerResponse from(ChatbotAnswerResult result) {
        return new ChatbotAnswerResponse(result.sessionId(), result.question(), result.answer(),
                result.remainingQuota(), result.createdAt());
    }
}
