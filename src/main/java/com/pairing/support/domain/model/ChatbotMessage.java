package com.pairing.support.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.support.exception.ChatbotErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 챗봇 질문 1건과 그 답변. 세션에 속한다.
 *
 * <p>답변에 딸린 이동 버튼({@link ChatbotIntent})도 함께 남긴다. 버튼을 눌러 다른 화면에 갔다가
 * 돌아오는 것이 정상 흐름인데, 저장하지 않으면 <b>버튼을 쓸수록 사라지는</b> 화면이 된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotMessage {

    private Long id;
    private Long sessionId;
    private String question;
    private String answer;
    private ChatbotIntent intent;
    private LocalDateTime createdAt;

    private ChatbotMessage(Long id, Long sessionId, String question, String answer, ChatbotIntent intent,
                           LocalDateTime createdAt) {
        if (sessionId == null || question == null || question.isBlank() || answer == null || answer.isBlank()) {
            throw new BusinessException(ChatbotErrorCode.INVALID_MESSAGE);
        }
        this.id = id;
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        // 옛 행에는 intent 가 없다. 버튼만 빠지고 대화는 그대로 보여야 한다.
        this.intent = intent == null ? ChatbotIntent.NONE : intent;
        this.createdAt = createdAt;
    }

    public static ChatbotMessage create(Long sessionId, String question, String answer, ChatbotIntent intent) {
        return new ChatbotMessage(null, sessionId, question, answer, intent, LocalDateTime.now());
    }

    public static ChatbotMessage reconstitute(Long id, Long sessionId, String question, String answer,
                                              ChatbotIntent intent, LocalDateTime createdAt) {
        return new ChatbotMessage(id, sessionId, question, answer, intent, createdAt);
    }
}
