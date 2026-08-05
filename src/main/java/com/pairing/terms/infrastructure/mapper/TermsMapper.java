package com.pairing.terms.infrastructure.mapper;

import com.pairing.terms.domain.model.Terms;
import com.pairing.terms.infrastructure.persistence.TermsJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TermsMapper {

    TermsJpaEntity toJpaEntity(Terms terms);

    default Terms toDomain(TermsJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Terms.reconstitute(
                entity.getId(),
                entity.getCode(),
                entity.getVersion(),
                entity.getTitle(),
                entity.getContent(),
                entity.isRequired(),
                entity.getTargetRole(),
                entity.getEffectiveAt()
        );
    }
}
