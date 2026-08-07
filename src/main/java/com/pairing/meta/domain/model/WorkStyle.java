package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 근무 방식. */
@Getter
@RequiredArgsConstructor
public enum WorkStyle {

    REMOTE("재택"),
    ONSITE("상주"),
    ANY("모두 가능");

    private final String label;
}
