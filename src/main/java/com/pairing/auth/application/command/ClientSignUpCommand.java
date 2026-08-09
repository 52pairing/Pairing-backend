package com.pairing.auth.application.command;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.terms.application.command.AgreeTermsCommand;

import java.util.List;

/** 클라이언트(기업) 회원가입. 이메일 인증을 마친 상태여야 한다. */
public record ClientSignUpCommand(
        String email,
        String password,
        String passwordConfirm,
        String name,
        String phone,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount,
        /** 기업 주소(필수). 계약서 갑 표시에 쓰인다. */
        String address,
        CardCommand card,
        BankAccountCommand bankAccount,
        List<AgreeTermsCommand> agreements,
        String userAgent
) {
}
