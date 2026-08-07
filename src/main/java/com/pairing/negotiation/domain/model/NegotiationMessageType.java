package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 협상 메시지 종류. */
@Getter
@RequiredArgsConstructor
public enum NegotiationMessageType {

    PROPOSAL("AI 제안"),
    RESPONSE("사용자 응답"),
    SYSTEM("시스템 안내");

    private final String label;
}
