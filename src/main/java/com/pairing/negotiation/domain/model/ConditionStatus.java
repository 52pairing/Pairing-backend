package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 조건별 협상 상태. */
@Getter
@RequiredArgsConstructor
public enum ConditionStatus {

    PENDING("협상중"),
    AGREED("합의"),
    REJECTED("재협상 필요"),
    FAILED("미합의");

    private final String label;
}
