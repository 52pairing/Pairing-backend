package com.pairing.chat.domain.repository;

import com.pairing.chat.domain.model.ChatRoom;

import java.util.List;
import java.util.Optional;

/** 채팅방 애그리거트(방 + 참여자) 리포지토리 포트. */
public interface ChatRoomRepository {

    ChatRoom save(ChatRoom chatRoom);

    /** 방 + 참여자를 함께 로드한다. */
    Optional<ChatRoom> findById(Long chatRoomId);

    /** 협상당 방은 하나(negotiation_id UNIQUE). 중복 생성 방지·화면 이동에 쓴다. */
    Optional<ChatRoom> findByNegotiationId(Long negotiationId);

    /** 내가 아직 나가지 않은 방 목록(최근 갱신순). 헤더 메시지 목록용. */
    List<ChatRoom> findActiveRoomsByAccountId(Long accountId);
}
