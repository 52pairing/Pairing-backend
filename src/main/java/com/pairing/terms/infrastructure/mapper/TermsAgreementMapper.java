package com.pairing.terms.infrastructure.mapper;

import com.pairing.terms.domain.model.TermsAgreement;
import com.pairing.terms.infrastructure.persistence.TermsAgreementJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TermsAgreementMapper {

    TermsAgreementJpaEntity toJpaEntity(TermsAgreement agreement);

    default TermsAgreement toDomain(TermsAgreementJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return TermsAgreement.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getTermsId(),
                entity.isAgreed(),
                entity.getAgreedAt(),
                entity.getUserAgent()
        );
    }
}
