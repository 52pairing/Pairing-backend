package com.pairing.settlement.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 위약금 상태. */
@Getter
@RequiredArgsConstructor
public enum PenaltyStatus {

    PENDING("납부 대기"),
    PAID("납부 완료"),
    FAILED("납부 실패");

    private final String label;
}
