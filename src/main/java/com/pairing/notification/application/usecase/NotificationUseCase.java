package com.pairing.notification.application.usecase;

import com.pairing.notification.application.result.NotificationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 로그인 계정 본인의 알림 조회·읽음 처리·삭제. */
public interface NotificationUseCase {

    Page<NotificationResult> findMine(Long accountId, boolean unreadOnly, Pageable pageable);

    int countUnread(Long accountId);

    /** 본인 알림이 아니면 {@code NT_002}, 없으면 {@code NT_001}. */
    void markAsRead(Long accountId, Long notificationId);

    void markAllAsRead(Long accountId);

    /** 본인 알림이 아니면 {@code NT_002}, 없으면 {@code NT_001}. */
    void delete(Long accountId, Long notificationId);

    void deleteAll(Long accountId);
}
