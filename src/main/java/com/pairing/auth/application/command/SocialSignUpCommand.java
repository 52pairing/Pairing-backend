package com.pairing.auth.application.command;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.domain.model.Address;
import com.pairing.terms.application.command.AgreeTermsCommand;

import java.time.LocalDate;
import java.util.List;

/**
 * 프리랜서 소셜 회원가입.
 *
 * <p>이메일은 티켓에 담긴 값을 쓴다. 요청 본문으로 받지 않으므로 "수정 불가"가 서버에서 지켜진다.
 */
public record SocialSignUpCommand(
        String signUpTicket,
        String name,
        String phone,
        LocalDate birthDate,
        Address address,
        CardCommand card,
        BankAccountCommand bankAccount,
        List<AgreeTermsCommand> agreements,
        String userAgent
) {
}
