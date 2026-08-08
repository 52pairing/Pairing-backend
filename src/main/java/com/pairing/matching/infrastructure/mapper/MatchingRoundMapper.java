package com.pairing.matching.infrastructure.mapper;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.infrastructure.persistence.MatchingRoundJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MatchingRoundMapper {

    default MatchingRoundJpaEntity toJpaEntity(MatchingRound round) {
        if (round == null) {
            return null;
        }
        return new MatchingRoundJpaEntity(
                round.getId(),
                round.getProjectId(),
                round.getPositionId(),
                round.getRoundNo(),
                round.getRoundType(),
                round.getRequestedCount(),
                round.getCostAmount(),
                round.getExposeCount(),
                round.getEmbeddingPoolSize(),
                round.isLowScoreWarned(),
                round.getStatus()
        );
    }

    default MatchingRound toDomain(MatchingRoundJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return MatchingRound.reconstitute(
                entity.getId(),
                entity.getProjectId(),
                entity.getPositionId(),
                entity.getRoundNo(),
                entity.getRoundType(),
                entity.getRequestedCount(),
                entity.getCostAmount(),
                entity.getExposeCount(),
                entity.getEmbeddingPoolSize(),
                entity.isLowScoreWarned(),
                entity.getStatus()
        );
    }
}
