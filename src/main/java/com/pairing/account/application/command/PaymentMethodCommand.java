package com.pairing.account.application.command;

import com.pairing.account.domain.model.PaymentMethodType;

/**
 * 결제/정산 수단 입력. 카드번호와 계좌번호는 여기까지만 평문이고,
 * 저장 직전에 {@code DataEncryptionPort}로 암호화된다.
 */
public record PaymentMethodCommand(
        PaymentMethodType methodType,
        String cardNumber,
        String cardBrand,
        String bankCode,
        String accountNo,
        String accountHolder
) {

    public static PaymentMethodCommand card(String cardNumber, String cardBrand) {
        return new PaymentMethodCommand(PaymentMethodType.CARD, cardNumber, cardBrand, null, null, null);
    }

    public static PaymentMethodCommand bankAccount(String bankCode, String accountNo, String accountHolder) {
        return new PaymentMethodCommand(PaymentMethodType.BANK_ACCOUNT, null, null, bankCode, accountNo,
                accountHolder);
    }
}
