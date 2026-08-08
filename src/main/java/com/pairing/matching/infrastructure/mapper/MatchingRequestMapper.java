package com.pairing.matching.infrastructure.mapper;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.infrastructure.persistence.MatchingRequestJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MatchingRequestMapper {

    default MatchingRequestJpaEntity toJpaEntity(MatchingRequest request) {
        if (request == null) {
            return null;
        }
        return new MatchingRequestJpaEntity(
                request.getId(),
                request.getProjectId(),
                request.getPositionId(),
                request.getCandidateId(),
                request.getFreelancerId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getExpiresAt(),
                request.getRespondedAt(),
                request.getRejectReason()
        );
    }

    default MatchingRequest toDomain(MatchingRequestJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return MatchingRequest.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getPositionId(),
                entity.getCandidateId(),
                entity.getFreelancerId(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getExpiresAt(),
                entity.getRespondedAt(),
                entity.getRejectReason()
        );
    }
}
