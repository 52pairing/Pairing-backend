package com.pairing.notification.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, Long> {

    Page<NotificationJpaEntity> findByOwnerAccountId(Long ownerAccountId, Pageable pageable);

    Page<NotificationJpaEntity> findByOwnerAccountIdAndReadFalse(Long ownerAccountId, Pageable pageable);

    long countByOwnerAccountIdAndReadFalse(Long ownerAccountId);

    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.read = true, n.readAt = CURRENT_TIMESTAMP "
            + "WHERE n.ownerAccountId = :ownerAccountId AND n.read = false")
    void markAllAsRead(@Param("ownerAccountId") Long ownerAccountId);

    void deleteByOwnerAccountId(Long ownerAccountId);
}
