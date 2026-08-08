package com.pairing.chat.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataChatRoomRepository extends JpaRepository<ChatRoomJpaEntity, Long> {

    @EntityGraph(attributePaths = "members")
    Optional<ChatRoomJpaEntity> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    Optional<ChatRoomJpaEntity> findByNegotiationId(Long negotiationId);

    /** 내가 아직 나가지 않은 방(최근 갱신순). 참여자는 지연 로딩으로 뒤에서 채운다. */
    @Query("SELECT DISTINCT r FROM ChatRoomJpaEntity r JOIN r.members m "
            + "WHERE m.accountId = :accountId AND m.leftAt IS NULL "
            + "ORDER BY r.updatedAt DESC")
    List<ChatRoomJpaEntity> findActiveRoomsByAccountId(@Param("accountId") Long accountId);
}
