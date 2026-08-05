package com.pairing.account.application.command;

import com.pairing.account.domain.model.SocialProvider;

import java.time.LocalDate;
import java.util.List;

/** 프리랜서 소셜 계정 + 소셜 연동 + 프로필 + 결제수단. 비밀번호가 없다. */
public record CreateSocialFreelancerAccountCommand(
        String email,
        String name,
        String phone,
        LocalDate birthDate,
        SocialProvider provider,
        String providerUid,
        String providerEmail,
        boolean providerEmailVerified,
        List<PaymentMethodCommand> paymentMethods
) {
}
