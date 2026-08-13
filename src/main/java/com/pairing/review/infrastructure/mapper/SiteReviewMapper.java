package com.pairing.review.infrastructure.mapper;

import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.infrastructure.persistence.SiteReviewJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SiteReviewMapper {

    SiteReviewJpaEntity toJpaEntity(SiteReview siteReview);

    default SiteReview toDomain(SiteReviewJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return SiteReview.reconstitute(entity.getId(), entity.getContractId(), entity.getProjectId(),
                entity.getWriterAccountId(), entity.getWriterRole(), entity.getScore(), entity.getContent(),
                entity.isPromoted(), entity.getCreatedAt());
    }
}
