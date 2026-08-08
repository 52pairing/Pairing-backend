package com.pairing.notification.infrastructure.persistence;

import com.pairing.notification.domain.model.Notification;
import com.pairing.notification.domain.repository.NotificationRepository;
import com.pairing.notification.infrastructure.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final SpringDataNotificationRepository springDataRepository;
    private final NotificationMapper notificationMapper;

    @Override
    public Notification save(Notification notification) {
        return notificationMapper.toDomain(springDataRepository.save(notificationMapper.toJpaEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return springDataRepository.findById(id).map(notificationMapper::toDomain);
    }

    @Override
    public Page<Notification> findByOwnerAccountId(Long ownerAccountId, Pageable pageable) {
        return springDataRepository.findByOwnerAccountId(ownerAccountId, pageable).map(notificationMapper::toDomain);
    }

    @Override
    public Page<Notification> findByOwnerAccountIdAndReadFalse(Long ownerAccountId, Pageable pageable) {
        return springDataRepository.findByOwnerAccountIdAndReadFalse(ownerAccountId, pageable)
                .map(notificationMapper::toDomain);
    }

    @Override
    public long countByOwnerAccountIdAndReadFalse(Long ownerAccountId) {
        return springDataRepository.countByOwnerAccountIdAndReadFalse(ownerAccountId);
    }

    @Override
    public void markAllAsRead(Long ownerAccountId) {
        springDataRepository.markAllAsRead(ownerAccountId);
    }

    @Override
    public void deleteById(Long id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public void deleteByOwnerAccountId(Long ownerAccountId) {
        springDataRepository.deleteByOwnerAccountId(ownerAccountId);
    }
}
