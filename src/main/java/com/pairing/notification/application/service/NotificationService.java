package com.pairing.notification.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.event.NotificationEvent;
import com.pairing.notification.application.port.out.NotificationEventPort;
import com.pairing.notification.application.result.NotificationResult;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.application.usecase.NotificationUseCase;
import com.pairing.notification.domain.model.Notification;
import com.pairing.notification.domain.repository.NotificationRepository;
import com.pairing.notification.exception.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService implements NotificationUseCase, NotificationCreateUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationEventPort notificationEventPort;

    @Override
    @Transactional
    public NotificationResult create(CreateNotificationCommand command) {
        Notification notification = Notification.create(command.ownerAccountId(), command.type(),
                command.title(), command.content(), command.linkUrl());
        Notification saved = notificationRepository.save(notification);

        notificationEventPort.publish(saved.getOwnerAccountId(), new NotificationEvent(saved.getId(),
                saved.getType(), saved.getTitle(), saved.getContent(), saved.getLinkUrl(), saved.getCreatedAt()));

        return toResult(saved);
    }

    @Override
    public Page<NotificationResult> findMine(Long accountId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByOwnerAccountIdAndReadFalse(accountId, pageable)
                : notificationRepository.findByOwnerAccountId(accountId, pageable);
        return page.map(this::toResult);
    }

    @Override
    public int countUnread(Long accountId) {
        return (int) notificationRepository.countByOwnerAccountIdAndReadFalse(accountId);
    }

    @Override
    @Transactional
    public void markAsRead(Long accountId, Long notificationId) {
        Notification notification = getOwned(accountId, notificationId);
        notification.markAsRead();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long accountId) {
        notificationRepository.markAllAsRead(accountId);
    }

    @Override
    @Transactional
    public void delete(Long accountId, Long notificationId) {
        Notification notification = getOwned(accountId, notificationId);
        notificationRepository.deleteById(notification.getId());
    }

    @Override
    @Transactional
    public void deleteAll(Long accountId) {
        notificationRepository.deleteByOwnerAccountId(accountId);
    }

    private Notification getOwned(Long accountId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(accountId)) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_FORBIDDEN);
        }
        return notification;
    }

    private NotificationResult toResult(Notification notification) {
        return new NotificationResult(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.getLinkUrl(), notification.isRead(),
                notification.getCreatedAt());
    }
}
