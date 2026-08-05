package com.pairing.account.application.command;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;

import java.util.List;

/** 클라이언트 계정 + 기업 프로필 + 결제수단을 한 번에 만든다. */
public record CreateClientAccountCommand(
        String email,
        String passwordHash,
        String name,
        String phone,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount,
        List<PaymentMethodCommand> paymentMethods
) {
}
