package com.pairing.notification.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.notification.exception.NotificationErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 1건. (요구사항 R27)
 *
 * <p>매칭/협상/계약/정산/문의 등 다른 도메인에서 이벤트가 발생했을 때 생성된다. 그 도메인들이
 * 아직 이 알림을 만들어 호출하는 지점이 없어서, 지금은 {@code NotificationCreateUseCase} 라는
 * 포트만 열어두고 실제 호출은 각 도메인 담당자가 넣는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private Long id;
    private Long ownerAccountId;
    private NotificationType type;
    private String title;
    private String content;
    private String linkUrl;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    private Notification(Long id, Long ownerAccountId, NotificationType type, String title, String content,
                         String linkUrl, boolean read, LocalDateTime readAt, LocalDateTime createdAt) {
        validate(ownerAccountId, type, title);
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.linkUrl = linkUrl;
        this.read = read;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public static Notification create(Long ownerAccountId, NotificationType type, String title, String content,
                                      String linkUrl) {
        return new Notification(null, ownerAccountId, type, title, content, linkUrl, false, null,
                LocalDateTime.now());
    }

    public static Notification reconstitute(Long id, Long ownerAccountId, NotificationType type, String title,
                                            String content, String linkUrl, boolean read, LocalDateTime readAt,
                                            LocalDateTime createdAt) {
        return new Notification(id, ownerAccountId, type, title, content, linkUrl, read, readAt, createdAt);
    }

    private static void validate(Long ownerAccountId, NotificationType type, String title) {
        if (ownerAccountId == null || type == null || title == null || title.isBlank()) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_FIELD);
        }
    }

    public boolean isOwnedBy(Long accountId) {
        return this.ownerAccountId.equals(accountId);
    }

    public void markAsRead() {
        if (this.read) {
            return;
        }
        this.read = true;
        this.readAt = LocalDateTime.now();
    }
}
