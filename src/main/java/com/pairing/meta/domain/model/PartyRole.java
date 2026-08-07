package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 거래 당사자 구분. 계약 서명, 협상 승인, 리뷰, 정산이 같은 값을 쓴다.
 *
 * <p>계정 역할(Role)과 값은 같지만 의미가 다르다. 이쪽은 "이 거래에서 어느 편인가"를 가리킨다.
 */
@Getter
@RequiredArgsConstructor
public enum PartyRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;
}
