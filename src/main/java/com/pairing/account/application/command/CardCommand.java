package com.pairing.account.application.command;

/**
 * 수수료 결제용 카드.
 *
 * <p>카드번호는 여기까지만 평문이다. 저장 직전에 암호화되고, 이후에는 끝 4자리만 조회된다.
 */
public record CardCommand(
        String cardNumber,
        String cardBrand,
        /** 가입 요청에는 없는 값이라 null 로 올 수 있다. 마이페이지 수정에서만 채워진다. */
        String cardHolder
) {
}
