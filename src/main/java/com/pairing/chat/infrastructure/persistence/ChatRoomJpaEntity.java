package com.pairing.chat.infrastructure.persistence;

import com.pairing.chat.domain.model.ChatRoomStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(name = "input_enabled", nullable = false)
    private boolean inputEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // 애그리거트: 참여자는 방과 생명주기를 함께한다(cascade + orphanRemoval).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private List<ChatRoomMemberJpaEntity> members = new ArrayList<>();

    public ChatRoomJpaEntity(Long id, Long negotiationId, boolean inputEnabled, ChatRoomStatus status,
                             LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt,
                             List<ChatRoomMemberJpaEntity> members) {
        this.id = id;
        this.negotiationId = negotiationId;
        this.inputEnabled = inputEnabled;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.members = members != null ? members : new ArrayList<>();
    }
}
