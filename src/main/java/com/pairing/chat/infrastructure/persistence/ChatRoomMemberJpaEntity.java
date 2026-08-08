package com.pairing.chat.infrastructure.persistence;

import com.pairing.chat.domain.model.ChatMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** chat_room_member. 부모(ChatRoomJpaEntity)의 @JoinColumn 이 chat_room_id 를 채운다. */
@Entity
@Table(name = "chat_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false, length = 20)
    private ChatMemberRole role;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ChatRoomMemberJpaEntity(Long id, Long accountId, ChatMemberRole role, LocalDateTime lastReadAt,
                                   LocalDateTime leftAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.role = role;
        this.lastReadAt = lastReadAt;
        this.leftAt = leftAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
