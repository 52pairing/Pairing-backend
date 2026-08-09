package com.pairing.support.infrastructure.mapper;

import com.pairing.support.domain.model.Inquiry;
import com.pairing.support.infrastructure.persistence.InquiryJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InquiryMapper {

    InquiryJpaEntity toJpaEntity(Inquiry inquiry);

    default Inquiry toDomain(InquiryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Inquiry.reconstitute(entity.getId(), entity.getWriterAccountId(), entity.getWriterName(),
                entity.getWriterRole(), entity.getWriterEmail(), entity.getTitle(), entity.getContent(),
                entity.getFileIds(), entity.getStatus(), entity.getAnswer(), entity.getAnsweredAt(),
                entity.getCreatedAt());
    }
}
