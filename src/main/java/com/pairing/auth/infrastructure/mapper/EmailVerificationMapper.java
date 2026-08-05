package com.pairing.auth.infrastructure.mapper;

import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.infrastructure.persistence.EmailVerificationJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EmailVerificationMapper {

    EmailVerificationJpaEntity toJpaEntity(EmailVerification emailVerification);

    default EmailVerification toDomain(EmailVerificationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return EmailVerification.reconstitute(
                entity.getId(),
                entity.getEmail(),
                entity.getPurpose(),
                entity.getCodeHash(),
                entity.getExpiresAt(),
                entity.getVerifiedAt(),
                entity.getAttemptCount()
        );
    }
}
