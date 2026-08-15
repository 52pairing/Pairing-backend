package com.pairing.account.application.command;

import com.pairing.account.domain.model.Address;

import java.time.LocalDate;

/** 프리랜서 일반(이메일) 계정 + 프로필 + 결제수단(카드·계좌). */
public record CreateFreelancerAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        LocalDate birthDate,
        /** 주소(필수). 2026-08-14 부터 가입 시점에 받는다. */
        Address address,
        CardCommand card,
        BankAccountCommand bankAccount
) {
}
