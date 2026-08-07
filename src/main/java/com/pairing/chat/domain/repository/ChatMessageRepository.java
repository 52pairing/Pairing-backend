package com.pairing.chat.domain.repository;

import com.pairing.chat.domain.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

/** 채팅 메시지 리포지토리 포트. */
public interface ChatMessageRepository {

    ChatMessage save(ChatMessage message);

    /** 방의 메시지 페이지(최신순). 과거로 스크롤하면 page 를 늘린다. */
    Page<ChatMessage> findByChatRoomId(Long chatRoomId, Pageable pageable);

    /** 방의 마지막 메시지(목록 카드의 미리보기용). */
    Optional<ChatMessage> findLastMessage(Long chatRoomId);

    /**
     * 안 읽은 메시지 수. {@code lastReadAt} 이후 도착했고 내가 보내지 않은 메시지를 센다.
     * lastReadAt 이 null 이면(아직 아무 것도 안 읽음) 내가 보내지 않은 전체를 센다.
     */
    int countUnread(Long chatRoomId, LocalDateTime lastReadAt, Long accountId);
}
