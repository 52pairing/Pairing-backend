package com.pairing.chat.infrastructure.mapper;

import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.model.ChatRoomMember;
import com.pairing.chat.infrastructure.persistence.ChatRoomJpaEntity;
import com.pairing.chat.infrastructure.persistence.ChatRoomMemberJpaEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 채팅방 애그리거트(방 + 참여자) ↔ JPA 매핑. */
@Component
public class ChatRoomMapper {

    public ChatRoomJpaEntity toJpaEntity(ChatRoom room) {
        List<ChatRoomMemberJpaEntity> members = new ArrayList<>();
        for (ChatRoomMember m : room.getMembers()) {
            members.add(toMemberJpa(m));
        }
        return new ChatRoomJpaEntity(room.getId(), room.getNegotiationId(), room.isInputEnabled(),
                room.getStatus(), room.getCreatedAt(), room.getUpdatedAt(), room.getClosedAt(), members);
    }

    public ChatRoom toDomain(ChatRoomJpaEntity e) {
        if (e == null) {
            return null;
        }
        List<ChatRoomMember> members = new ArrayList<>();
        for (ChatRoomMemberJpaEntity m : e.getMembers()) {
            members.add(toMemberDomain(m));
        }
        return ChatRoom.reconstitute(e.getId(), e.getNegotiationId(), e.isInputEnabled(), e.getStatus(),
                members, e.getCreatedAt(), e.getUpdatedAt(), e.getClosedAt());
    }

    private ChatRoomMemberJpaEntity toMemberJpa(ChatRoomMember m) {
        return new ChatRoomMemberJpaEntity(m.getId(), m.getAccountId(), m.getRole(), m.getLastReadAt(),
                m.getLeftAt(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private ChatRoomMember toMemberDomain(ChatRoomMemberJpaEntity m) {
        return ChatRoomMember.reconstitute(m.getId(), m.getAccountId(), m.getRole(), m.getLastReadAt(),
                m.getLeftAt(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
