package com.pairing.support.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question", nullable = false, length = 500)
    private String question;

    @Column(name = "answer", nullable = false, length = 2000)
    private String answer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChatbotMessageJpaEntity(Long id, Long sessionId, String question, String answer,
                                   LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }
}
