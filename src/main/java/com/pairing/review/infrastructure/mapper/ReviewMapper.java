package com.pairing.review.infrastructure.mapper;

import com.pairing.review.domain.model.Review;
import com.pairing.review.infrastructure.persistence.ReviewJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReviewMapper {

    ReviewJpaEntity toJpaEntity(Review review);

    default Review toDomain(ReviewJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Review.reconstitute(entity.getId(), entity.getContractId(), entity.getProjectId(),
                entity.getReviewerAccountId(), entity.getReviewerRole(), entity.getRevieweeAccountId(),
                entity.getRevieweeRole(), entity.getScore(), entity.getContent(), entity.getCreatedAt());
    }
}
