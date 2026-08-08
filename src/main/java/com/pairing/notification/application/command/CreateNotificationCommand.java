package com.pairing.notification.application.command;

import com.pairing.notification.domain.model.NotificationType;

/**
 * 알림 생성 요청. (다른 도메인이 {@code NotificationCreateUseCase.create(...)} 를 호출할 때 사용)
 *
 * <p>{@code linkUrl} 은 알림을 눌렀을 때 이동할 화면 경로다. 호출하는 도메인이
 * {@code type} 에 맞는 경로를 직접 채워서 넘긴다.
 */
public record CreateNotificationCommand(
        Long ownerAccountId,
        NotificationType type,
        String title,
        String content,
        String linkUrl
) {
}
