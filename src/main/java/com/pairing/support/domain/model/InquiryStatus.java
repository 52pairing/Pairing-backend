package com.pairing.support.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 1:1 문의 상태. (요구사항 R45) */
@Getter
@RequiredArgsConstructor
public enum InquiryStatus {

    PENDING("대기중"),
    ANSWERED("답변완료");

    private final String label;
}
