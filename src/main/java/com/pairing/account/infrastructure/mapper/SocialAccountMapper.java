package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.infrastructure.persistence.SocialAccountJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SocialAccountMapper {

    SocialAccountJpaEntity toJpaEntity(SocialAccount socialAccount);

    default SocialAccount toDomain(SocialAccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return SocialAccount.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getProvider(),
                entity.getProviderUid(),
                entity.getProviderEmail(),
                entity.isEmailVerified(),
                entity.getConnectedAt()
        );
    }
}
