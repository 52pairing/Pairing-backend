package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 기간 단위. 최대 24개월 / 24주. */
@Getter
@RequiredArgsConstructor
public enum PeriodUnit {

    MONTH("개월"),
    WEEK("주");

    private final String label;
}
