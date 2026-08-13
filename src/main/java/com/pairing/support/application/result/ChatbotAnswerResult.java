package com.pairing.support.application.result;

import com.pairing.support.domain.model.ChatbotIntent;

import java.time.LocalDateTime;

/**
 * 챗봇 답변 한 건.
 *
 * <p>{@code intent} 는 함께 저장한다. 버튼을 눌러 다른 화면에 갔다가 돌아오는 것이 정상 흐름인데,
 * 저장하지 않으면 <b>버튼을 쓸수록 사라지는</b> 화면이 된다. 지난 대화를 불러올 때도 그대로 복원된다.
 *
 * <p>다만 {@code intent} 컬럼이 없던 시절의 대화는 {@code NONE} 으로 올라온다. 버튼만 빠지고
 * 질문·답변은 그대로 보인다.
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
