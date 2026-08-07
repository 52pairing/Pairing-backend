package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 직군. 프리랜서 조건과 프로젝트 포지션이 공유한다. */
@Getter
@RequiredArgsConstructor
public enum JobCategory {

    DEVELOPMENT("개발"),
    DESIGN("디자인");

    private final String label;
}
