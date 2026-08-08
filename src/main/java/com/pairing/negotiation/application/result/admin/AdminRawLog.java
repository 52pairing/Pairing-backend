package com.pairing.negotiation.application.result.admin;

import java.time.LocalDateTime;

/**
 * ai_agent_log 한 줄(가공 없음). 장애 분석용 원본 로그.
 *
 * <p>이 테이블은 AI 서비스(파이썬)가 기록하고 백엔드는 읽기만 한다. round 개념 컬럼이 없어
 * roundNo 는 제공하지 않는다(응답에서 null).
 */
public record AdminRawLog(
        Long logId,
        String agentType,
        String model,
        String requestPayload,
        String responsePayload,
        Integer inputTokens,
        Integer outputTokens,
        Integer durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
