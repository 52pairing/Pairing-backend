package com.pairing.notification.application.event;

import com.pairing.notification.domain.model.NotificationType;

import java.time.LocalDateTime;

/**
 * 새 알림 실시간 push. 구독 경로 {@code /topic/users/{ownerAccountId}/notifications} 로 나간다.
 *
 * <p>대상 계정은 구독 경로 자체로 구분되므로 payload 에는 알림 내용만 담는다.
 */
public record NotificationEvent(
        Long notificationId,
        NotificationType type,
        String title,
        String content,
        String linkUrl,
        LocalDateTime createdAt
) {
}
