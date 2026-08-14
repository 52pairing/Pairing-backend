package com.pairing.auth.application.command;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.domain.model.Address;
import com.pairing.terms.application.command.AgreeTermsCommand;

import java.time.LocalDate;
import java.util.List;

/** 프리랜서 일반(이메일) 회원가입. */
public record FreelancerSignUpCommand(
        String email,
        String password,
        String passwordConfirm,
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
