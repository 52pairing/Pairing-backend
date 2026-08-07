package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 포지션(모집 단위) 상태. */
@Getter
@RequiredArgsConstructor
public enum PositionStatus {

    RECRUITING("모집중"),
    FILLED("모집 완료"),
    CLOSED("모집 종료");

    private final String label;
}
