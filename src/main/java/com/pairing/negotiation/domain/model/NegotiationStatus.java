package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 협상 상태. 결렬은 포기·최종 승인 거부·라운드 소진을 모두 포함한다. */
@Getter
@RequiredArgsConstructor
public enum NegotiationStatus {

    IN_PROGRESS("협상중"),
    AGREED("타결"),
    FAILED("협상 결렬");

    private final String label;
}
