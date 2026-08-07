package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 근무 형태. */
@Getter
@RequiredArgsConstructor
public enum WorkForm {

    FULL_TIME("풀타임"),
    PART_TIME("파트타임"),
    ANY("모두 가능");

    private final String label;
}
