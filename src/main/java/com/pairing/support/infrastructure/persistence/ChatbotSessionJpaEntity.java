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
@Table(name = "chatbot_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChatbotSessionJpaEntity(Long id, Long ownerAccountId, LocalDateTime createdAt) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.createdAt = createdAt;
    }
}
