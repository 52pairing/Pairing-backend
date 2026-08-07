package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 협상 당사자 구분. 마지노선·승인 등에서 클라이언트/프리랜서를 나눈다. */
@Getter
@RequiredArgsConstructor
public enum PartyRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;
}
