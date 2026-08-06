package com.pairing.account.application.command;

/**
 * 수수료 결제용 카드.
 *
 * <p>카드번호는 여기까지만 평문이다. 저장 직전에 암호화되고, 이후에는 끝 4자리만 조회된다.
 */
public record CardCommand(
        String cardNumber,
        String cardBrand
) {
}
