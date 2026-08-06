package com.pairing.account.application.command;

import java.time.LocalDate;

/** 프리랜서 일반(이메일) 계정 + 프로필. */
public record CreateFreelancerAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        LocalDate birthDate
) {
}
