package com.pairing.settlement.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 정산 상태. 결제는 버튼으로 수동 처리한다. */
@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

    PENDING("결제 대기"),
    PAID("결제 완료"),
    OVERDUE("미납"),
    FAILED("결제 실패");

    private final String label;
}
