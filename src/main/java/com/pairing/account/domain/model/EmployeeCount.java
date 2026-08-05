package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 클라이언트 기업 규모. */
@Getter
@RequiredArgsConstructor
public enum EmployeeCount {

    SIZE_1_4("1~4명"),
    SIZE_5_9("5~9명"),
    SIZE_10_49("10~49명"),
    SIZE_50_299("50~299명"),
    SIZE_300_OVER("300명 이상");

    private final String label;
}
