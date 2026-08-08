package com.pairing.notification.domain.repository;

import com.pairing.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    Page<Notification> findByOwnerAccountId(Long ownerAccountId, Pageable pageable);

    Page<Notification> findByOwnerAccountIdAndReadFalse(Long ownerAccountId, Pageable pageable);

    long countByOwnerAccountIdAndReadFalse(Long ownerAccountId);

    /** 안 읽은 알림을 한 번에 읽음 처리한다(각 건 재조회 없이). */
    void markAllAsRead(Long ownerAccountId);

    void deleteById(Long id);

    void deleteByOwnerAccountId(Long ownerAccountId);
}
