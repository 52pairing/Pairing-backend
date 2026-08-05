package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.PaymentMethod;

import java.util.List;

public interface PaymentMethodRepository {

    PaymentMethod save(PaymentMethod paymentMethod);

    List<PaymentMethod> saveAll(List<PaymentMethod> paymentMethods);

    List<PaymentMethod> findAllByAccountId(Long accountId);
}
