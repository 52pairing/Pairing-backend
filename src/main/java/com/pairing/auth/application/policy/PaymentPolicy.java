package com.pairing.auth.application.policy;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;

/**
 * 카드·계좌 번호 정규화.
 *
 * <p>사용자는 하이픈이나 공백을 섞어 입력한다. 그대로 저장하면 같은 카드가 다른 암호문으로 남고
 * 나중에 비교·이체 연동에서 어긋난다. 숫자만 남겨 저장한다.
 */
public final class PaymentPolicy {

    private PaymentPolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static CardCommand normalize(CardCommand card) {
        if (card == null) {
            return null;
        }
        return new CardCommand(digitsOnly(card.cardNumber()), trim(card.cardBrand()));
    }

    public static BankAccountCommand normalize(BankAccountCommand bankAccount) {
        if (bankAccount == null) {
            return null;
        }
        return new BankAccountCommand(
                trim(bankAccount.bankCode()),
                digitsOnly(bankAccount.accountNo()),
                trim(bankAccount.accountHolder())
        );
    }

    private static String digitsOnly(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
