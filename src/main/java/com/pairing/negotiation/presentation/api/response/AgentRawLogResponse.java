package com.pairing.negotiation.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * [관리자] AI 원본 로그 한 줄. (AI Agent 관리 &gt; 상세 &gt; 원본 로그 탭)
 *
 * <p>{@code ai_agent_log} 테이블을 가공 없이 그대로 보여준다. 장애 분석용이라
 * 프롬프트와 응답 원문, 모델명, 토큰 수, 소요 시간을 함께 준다.
 */
@Schema(description = "AI 원본 로그")
public record AgentRawLogResponse(

        @Schema(description = "로그 ID", example = "9001") Long logId,
        @Schema(description = "라운드 번호", example = "1") Integer roundNo,
        @Schema(description = "에이전트 구분", example = "CLIENT_AGENT") String agentType,
        @Schema(description = "사용 모델", example = "gemini-2.5-flash") String model,
        @Schema(description = "요청 프롬프트 원문") String requestPayload,
        @Schema(description = "응답 원문") String responsePayload,
        @Schema(description = "입력 토큰 수", example = "1820") Integer inputTokens,
        @Schema(description = "출력 토큰 수", example = "410") Integer outputTokens,
        @Schema(description = "소요 시간(ms)", example = "2140") Integer durationMs,
        @Schema(description = "실패 사유. 성공이면 null") String errorMessage,
        @Schema(description = "기록 시각") LocalDateTime createdAt
) {
}
