package com.pairing.client.application.command;

import com.pairing.account.domain.model.EmployeeCount;

/** {@code logoFileId} 는 null 이면 기존 기업 로고를 유지한다. */
public record ClientProfileUpdateCommand(
        Long accountId,
        String companyName,
        EmployeeCount employeeCount,
        String phone,
        String address,
        Long logoFileId
) {
}
