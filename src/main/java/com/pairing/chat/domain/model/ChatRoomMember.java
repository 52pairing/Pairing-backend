package com.pairing.chat.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅방 참여자. 방 하나에 클라이언트·프리랜서 두 명이 들어간다.
 *
 * <p>{@code lastReadAt} 은 안 읽은 메시지 수 계산의 기준선이고, {@code leftAt} 은 나가기 시각이다.
 * 방 애그리거트({@link ChatRoom})의 일부라 방을 통해서만 생성·변경한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    private Long id;
    private Long accountId;
    private ChatMemberRole role;
    private LocalDateTime lastReadAt;
    private LocalDateTime leftAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ChatRoomMember(Long id, Long accountId, ChatMemberRole role, LocalDateTime lastReadAt,
                           LocalDateTime leftAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.role = role;
        this.lastReadAt = lastReadAt;
        this.leftAt = leftAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 새 참여자. 방 생성 시점엔 아직 아무 것도 읽지 않은 상태(lastReadAt=null). */
    public static ChatRoomMember join(Long accountId, ChatMemberRole role) {
        LocalDateTime now = LocalDateTime.now();
        return new ChatRoomMember(null, accountId, role, null, null, now, now);
    }

    public static ChatRoomMember reconstitute(Long id, Long accountId, ChatMemberRole role,
                                              LocalDateTime lastReadAt, LocalDateTime leftAt,
                                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new ChatRoomMember(id, accountId, role, lastReadAt, leftAt, createdAt, updatedAt);
    }

    /** 여기까지 읽음. 안 읽은 수 배지의 기준선을 현재로 올린다. */
    public void markRead(LocalDateTime at) {
        this.lastReadAt = at;
        this.updatedAt = at;
    }

    /** 방 나가기. 나간 시각을 기록한다(행은 남겨 이력을 보존한다). */
    public void leave(LocalDateTime at) {
        this.leftAt = at;
        this.updatedAt = at;
    }

    public boolean hasLeft() {
        return leftAt != null;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.accountId != null && this.accountId.equals(accountId);
    }
}
