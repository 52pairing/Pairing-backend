package com.pairing.account.application.command;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;

/** 클라이언트 계정 + 기업 프로필 + 결제수단(카드·계좌)을 한 번에 만든다. */
public record CreateClientAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount,
        /** 기업 주소(필수). 계약서 갑 표시에 쓰인다. */
        String address,
        CardCommand card,
        BankAccountCommand bankAccount
) {
}
