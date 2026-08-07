package com.pairing.freelancer.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 졸업 구분. */
@Getter
@RequiredArgsConstructor
public enum GraduationStatus {

    GRADUATED("졸업"),
    EXPECTED("졸업예정"),
    ATTENDING("재학중"),
    LEAVE("휴학"),
    DROPPED("중퇴");

    private final String label;
}
