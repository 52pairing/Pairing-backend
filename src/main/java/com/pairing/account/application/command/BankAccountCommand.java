package com.pairing.account.application.command;

/**
 * 용역비 수령용 계좌.
 *
 * <p>{@code bankCode} 는 금융결제원 기관코드다. (예: 088 신한은행)
 */
public record BankAccountCommand(
        String bankCode,
        String accountNo,
        String accountHolder
) {
}
