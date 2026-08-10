package com.pairing.client.application.command;

import com.pairing.account.domain.model.EmployeeCount;

public record ClientProfileUpdateCommand(
        Long accountId,
        String companyName,
        EmployeeCount employeeCount,
        String phone,
        String address
) {
}
