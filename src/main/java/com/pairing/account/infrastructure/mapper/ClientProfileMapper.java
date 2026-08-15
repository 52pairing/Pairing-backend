package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.Address;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.infrastructure.persistence.AddressEmbeddable;
import com.pairing.account.infrastructure.persistence.ClientProfileJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClientProfileMapper {

    ClientProfileJpaEntity toJpaEntity(ClientProfile clientProfile);

    default ClientProfile toDomain(ClientProfileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ClientProfile.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getCompanyName(),
                entity.getBusinessNo(),
                entity.getBusinessField(),
                entity.getEmployeeCount(),
                entity.getAddress(),
                AddressMapping.toDomain(entity.getAddressParts()),
                entity.getLogoFileId(),
                entity.getGrade(),
                entity.getGradeCheckedAt(),
                entity.getDeletedAt()
        );
    }

    default AddressEmbeddable toEmbeddable(Address address) {
        return AddressMapping.toEmbeddable(address);
    }
}
