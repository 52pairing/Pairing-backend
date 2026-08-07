package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethodType {

    CARD("카드(수수료 결제)"),
    EASY_PAY("간편결제(수수료 결제)"),
    BANK_ACCOUNT("계좌(용역비 수령)");

    private final String label;
}
