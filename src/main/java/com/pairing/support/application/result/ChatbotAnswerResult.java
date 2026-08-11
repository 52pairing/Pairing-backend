package com.pairing.support.application.result;

import com.pairing.support.domain.model.ChatbotIntent;

import java.time.LocalDateTime;

/**
 * 챗봇 답변 한 건.
 *
 * <p>{@code intent} 는 저장하지 않는다. 방금 받은 답변에만 버튼을 띄우고, 지난 대화를 다시
 * 불러올 때는 텍스트만 보여준다. 하루 10질문 제한이라 이력을 되짚어 볼 일이 적어서,
 * 컬럼을 늘리는 값보다 안 늘리는 값이 크다고 봤다. 그래서 이력 조회는 항상 {@code NONE} 이다.
 */
public record ChatbotAnswerResult(
        Long sessionId,
        String question,
        String answer,
        ChatbotIntent intent,
        int remainingQuota,
        LocalDateTime createdAt
) {
}
