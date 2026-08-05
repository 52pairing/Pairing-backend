package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.repository.PaymentMethodRepository;
import com.pairing.account.infrastructure.mapper.PaymentMethodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentMethodRepositoryAdapter implements PaymentMethodRepository {

    private final SpringDataPaymentMethodRepository springDataRepository;
    private final PaymentMethodMapper paymentMethodMapper;

    @Override
    public PaymentMethod save(PaymentMethod paymentMethod) {
        PaymentMethodJpaEntity saved = springDataRepository.save(paymentMethodMapper.toJpaEntity(paymentMethod));
        return paymentMethodMapper.toDomain(saved);
    }

    @Override
    public List<PaymentMethod> saveAll(List<PaymentMethod> paymentMethods) {
        List<PaymentMethodJpaEntity> entities = paymentMethods.stream()
                .map(paymentMethodMapper::toJpaEntity)
                .toList();

        return springDataRepository.saveAll(entities).stream()
                .map(paymentMethodMapper::toDomain)
                .toList();
    }

    @Override
    public List<PaymentMethod> findAllByAccountId(Long accountId) {
        return springDataRepository.findAllByAccountIdAndDeletedAtIsNull(accountId).stream()
                .map(paymentMethodMapper::toDomain)
                .toList();
    }
}
