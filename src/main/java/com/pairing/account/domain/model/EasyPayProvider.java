package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 간편결제 공급자. 마이페이지 결제수단 목록에 "카카오페이 · 계좌 연동" 처럼 표시된다. */
@Getter
@RequiredArgsConstructor
public enum EasyPayProvider {

    KAKAO_PAY("카카오페이"),
    NAVER_PAY("네이버페이"),
    TOSS_PAY("토스페이");

    private final String label;
}
