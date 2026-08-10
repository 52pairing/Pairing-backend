package com.pairing.support.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.support.exception.ChatbotErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 챗봇 질문 1건과 그 답변. 세션에 속한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotMessage {

    private Long id;
    private Long sessionId;
    private String question;
    private String answer;
    private LocalDateTime createdAt;

    private ChatbotMessage(Long id, Long sessionId, String question, String answer, LocalDateTime createdAt) {
        if (sessionId == null || question == null || question.isBlank() || answer == null || answer.isBlank()) {
            throw new BusinessException(ChatbotErrorCode.INVALID_MESSAGE);
        }
        this.id = id;
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }

    public static ChatbotMessage create(Long sessionId, String question, String answer) {
        return new ChatbotMessage(null, sessionId, question, answer, LocalDateTime.now());
    }

    public static ChatbotMessage reconstitute(Long id, Long sessionId, String question, String answer,
                                              LocalDateTime createdAt) {
        return new ChatbotMessage(id, sessionId, question, answer, createdAt);
    }
}
