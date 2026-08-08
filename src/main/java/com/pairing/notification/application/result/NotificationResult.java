package com.pairing.notification.application.result;

import com.pairing.notification.domain.model.NotificationType;

import java.time.LocalDateTime;

public record NotificationResult(
        Long notificationId,
        NotificationType type,
        String title,
        String content,
        String linkUrl,
        boolean read,
        LocalDateTime createdAt
) {
}
