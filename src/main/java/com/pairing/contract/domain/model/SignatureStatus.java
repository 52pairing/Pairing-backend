package com.pairing.contract.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 당사자별 서명 상태. */
@Getter
@RequiredArgsConstructor
public enum SignatureStatus {

    PENDING("서명 대기"),
    SIGNED("서명 완료"),
    REJECTED("서명 거부");

    private final String label;
}
