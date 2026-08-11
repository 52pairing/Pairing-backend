package com.pairing.support.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataChatbotMessageRepository extends JpaRepository<ChatbotMessageJpaEntity, Long> {

    /**
     * 계정의 하루치 대화. 메시지는 세션에만 매달려 있어 계정으로 바로 못 찾는다. 세션을 거쳐 좁힌다.
     *
     * <p>세션마다 나눠 조회하면 세션 수만큼 쿼리가 나가므로 한 번에 가져온다.
     */
    @Query("""
            select m from ChatbotMessageJpaEntity m
            where m.sessionId in (
                    select s.id from ChatbotSessionJpaEntity s where s.ownerAccountId = :accountId)
              and m.createdAt >= :from and m.createdAt < :until
            order by m.createdAt asc
            """)
    List<ChatbotMessageJpaEntity> findByOwnerAccountIdAndCreatedAtRange(@Param("accountId") Long accountId,
                                                                       @Param("from") LocalDateTime from,
                                                                       @Param("until") LocalDateTime until);
}
