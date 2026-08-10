package com.pairing.support.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 챗봇 대화 세션. 이어서 묻기 위한 묶음일 뿐, 내용 자체는 {@link ChatbotMessage}가 갖는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotSession {

    private Long id;
    private Long ownerAccountId;
    private LocalDateTime createdAt;

    private ChatbotSession(Long id, Long ownerAccountId, LocalDateTime createdAt) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.createdAt = createdAt;
    }

    public static ChatbotSession create(Long ownerAccountId) {
        return new ChatbotSession(null, ownerAccountId, LocalDateTime.now());
    }

    public static ChatbotSession reconstitute(Long id, Long ownerAccountId, LocalDateTime createdAt) {
        return new ChatbotSession(id, ownerAccountId, createdAt);
    }

    public boolean isOwnedBy(Long accountId) {
        return this.ownerAccountId.equals(accountId);
    }
}
