package com.pairing.notification.infrastructure.mapper;

import com.pairing.notification.domain.model.Notification;
import com.pairing.notification.infrastructure.persistence.NotificationJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface NotificationMapper {

    NotificationJpaEntity toJpaEntity(Notification notification);

    default Notification toDomain(NotificationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Notification.reconstitute(entity.getId(), entity.getOwnerAccountId(), entity.getType(),
                entity.getTitle(), entity.getContent(), entity.getLinkUrl(), entity.isRead(), entity.getReadAt(),
                entity.getCreatedAt());
    }
}
