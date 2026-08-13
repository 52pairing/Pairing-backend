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
     * 안 읽은 수 — <b>한 번도 읽지 않은 방</b>(lastReadAt 이 null). 시간 조건 없이 전부 센다.
     *
     * <p><b>왜 쿼리를 둘로 쪼갰나.</b> 원래는 {@code (:lastReadAt IS NULL OR m.createdAt > :lastReadAt)}
     * 한 벌이었는데, <b>PostgreSQL 에서 500 이 났다</b>:
     *
     * <pre>ERROR: could not determine data type of parameter $2</pre>
     *
     * {@code ? is null} 은 파라미터가 컬럼과 비교되지 않아 <b>Postgres 가 타입을 추론할 수단이 없다.</b>
     * 값이 null 인지와 무관하게 <b>SQL 파싱 단계에서</b> 실패하므로, 이 경로를 타는 요청은 항상 터졌다.
     *
     * <p><b>테스트가 못 잡은 이유.</b> 테스트 H2 는 {@code MODE=PostgreSQL} 이지만 이 구문을 통과시킨다.
     * 방을 새로 열면 {@code lastReadAt} 이 null 이라 반드시 이 경로를 타는데도 초록불이었다.
     * 그래서 캐스팅으로 우회하지 않고 <b>파라미터를 IS NULL 비교에서 아예 빼는</b> 방식으로 고쳤다 —
     * 방언에 기대지 않는다.
     */
    @Query("SELECT COUNT(m) FROM ChatMessageJpaEntity m "
            + "WHERE m.chatRoomId = :roomId "
            + "AND (m.senderId IS NULL OR m.senderId <> :accountId)")
    long countUnreadAll(@Param("roomId") Long roomId,
                        @Param("accountId") Long accountId);

    /** 안 읽은 수 — lastReadAt 이후 도착했고 내가 보내지 않은 메시지(시스템 메시지 포함). */
    @Query("SELECT COUNT(m) FROM ChatMessageJpaEntity m "
            + "WHERE m.chatRoomId = :roomId "
            + "AND m.createdAt > :lastReadAt "
            + "AND (m.senderId IS NULL OR m.senderId <> :accountId)")
    long countUnreadSince(@Param("roomId") Long roomId,
                          @Param("lastReadAt") LocalDateTime lastReadAt,
                          @Param("accountId") Long accountId);
}
