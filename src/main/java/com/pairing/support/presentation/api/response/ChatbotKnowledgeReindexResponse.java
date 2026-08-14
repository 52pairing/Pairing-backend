package com.pairing.support.presentation.api.response;

import com.pairing.support.application.port.out.ChatbotAiPort;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "챗봇 지식 재색인 결과")
public record ChatbotKnowledgeReindexResponse(

        @Schema(description = "AI 서버가 가진 지식 조각 전체 수", example = "14")
        int total,

        @Schema(description = "이번에 새로 임베딩한 수(신규이거나 문구가 바뀐 것)", example = "14")
        int embedded,

        @Schema(description = "문구가 그대로여서 건너뛴 수", example = "0")
        int skipped
) {

    public static ChatbotKnowledgeReindexResponse from(ChatbotAiPort.KnowledgeReindexResult result) {
        return new ChatbotKnowledgeReindexResponse(result.total(), result.embedded(), result.skipped());
    }
}
