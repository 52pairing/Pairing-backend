package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 희망 급여 단위. 금액은 만원 단위로 입력받는다. */
@Getter
@RequiredArgsConstructor
public enum PayUnit {

    HOURLY("시급"),
    DAILY("일급"),
    MONTHLY("월급");

    private final String label;
}
