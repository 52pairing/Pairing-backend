package com.pairing.matching.infrastructure.mapper;

import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.infrastructure.persistence.MatchingSnapshotJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MatchingSnapshotMapper {

    default MatchingSnapshotJpaEntity toJpaEntity(MatchingSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new MatchingSnapshotJpaEntity(
                snapshot.getId(),
                snapshot.getProjectId(),
                snapshot.getPositionId(),
                snapshot.getFreelancerId(),
                snapshot.getSnapshotType(),
                snapshot.getSnapshotJson()
        );
    }

    default MatchingSnapshot toDomain(MatchingSnapshotJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return MatchingSnapshot.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getPositionId(),
                entity.getFreelancerId(),
                entity.getSnapshotType(),
                entity.getSnapshotJson()
        );
    }
}
