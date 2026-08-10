package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.infrastructure.persistence.FreelancerProfileJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FreelancerProfileMapper {

    FreelancerProfileJpaEntity toJpaEntity(FreelancerProfile freelancerProfile);

    default FreelancerProfile toDomain(FreelancerProfileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return FreelancerProfile.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getBirthDate(),
                entity.getAddress(),
                entity.getProfileFileId(),
                entity.isAiMatchingAgreed(),
                entity.isMatchingPaused(),
                entity.getGrade(),
                entity.getGradeCheckedAt(),
                entity.getDeletedAt()
        );
    }
}
