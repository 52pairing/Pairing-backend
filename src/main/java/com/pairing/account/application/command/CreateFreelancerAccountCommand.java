package com.pairing.account.application.command;

import java.time.LocalDate;
import java.util.List;

/** 프리랜서 일반(이메일) 계정 + 프로필 + 결제수단. */
public record CreateFreelancerAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        LocalDate birthDate,
        List<PaymentMethodCommand> paymentMethods
) {
}
