package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.infrastructure.persistence.PaymentMethodJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMethodMapper {

    PaymentMethodJpaEntity toJpaEntity(PaymentMethod paymentMethod);

    default PaymentMethod toDomain(PaymentMethodJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PaymentMethod.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getMethodType(),
                entity.getCardNumberEnc(),
                entity.getCardBrand(),
                entity.getCardLast4(),
                entity.getCardHolder(),
                entity.getBankCode(),
                entity.getAccountNoEnc(),
                entity.getAccountLast4(),
                entity.getAccountHolder(),
                entity.getDeletedAt()
        );
    }
}
