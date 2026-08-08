package com.pairing.chat.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SpringDataChatMessageRepository extends JpaRepository<ChatMessageJpaEntity, Long> {

    Page<ChatMessageJpaEntity> findByChatRoomId(Long chatRoomId, Pageable pageable);

    Optional<ChatMessageJpaEntity> findFirstByChatRoomIdOrderByCreatedAtDescIdDesc(Long chatRoomId);

    /**
     * 안 읽은 수. lastReadAt 이후 도착했고 내가 보내지 않은 메시지(시스템 메시지 포함).
     * lastReadAt 이 null 이면 시간 조건은 건너뛴다.
     */
    @Query("SELECT COUNT(m) FROM ChatMessageJpaEntity m "
            + "WHERE m.chatRoomId = :roomId "
            + "AND (:lastReadAt IS NULL OR m.createdAt > :lastReadAt) "
            + "AND (m.senderId IS NULL OR m.senderId <> :accountId)")
    long countUnread(@Param("roomId") Long roomId,
                     @Param("lastReadAt") LocalDateTime lastReadAt,
                     @Param("accountId") Long accountId);
}
