package com.pairing.matching.infrastructure.mapper;

import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.infrastructure.persistence.MatchingCandidateJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MatchingCandidateMapper {

    default MatchingCandidateJpaEntity toJpaEntity(MatchingCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        return new MatchingCandidateJpaEntity(
                candidate.getId(),
                candidate.getRoundId(),
                candidate.getPositionId(),
                candidate.getFreelancerId(),
                candidate.getStage(),
                candidate.getSimilarity(),
                candidate.getBaseScore(),
                candidate.getGradeWeight(),
                candidate.getFitScore(),
                candidate.getGuardPassed(),
                candidate.getGuardReason(),
                candidate.getFitReason(),
                candidate.getRankNo(),
                candidate.isExposed(),
                candidate.isRejected()
        );
    }

    default MatchingCandidate toDomain(MatchingCandidateJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return MatchingCandidate.reconstitute(
                entity.getId(),
                entity.getRoundId(),
                entity.getPositionId(),
                entity.getFreelancerId(),
                entity.getStage(),
                entity.getSimilarity(),
                entity.getBaseScore(),
                entity.getGradeWeight(),
                entity.getFitScore(),
                entity.getGuardPassed(),
                entity.getGuardReason(),
                entity.getFitReason(),
                entity.getRankNo(),
                entity.isExposed(),
                entity.isRejected()
        );
    }
}
