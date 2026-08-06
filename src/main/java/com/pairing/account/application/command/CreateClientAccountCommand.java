package com.pairing.account.application.command;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;

/** 클라이언트 계정 + 기업 프로필을 한 번에 만든다. (결제수단 등록은 마이페이지 소관) */
public record CreateClientAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount
) {
}
