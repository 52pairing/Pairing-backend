package com.pairing.terms.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 약관 종류. 버전은 code + version 조합으로 관리한다. */
@Getter
@RequiredArgsConstructor
public enum TermsCode {

    SERVICE_CLIENT("클라이언트 서비스 이용약관"),
    SERVICE_FREELANCER("프리랜서 서비스 이용약관"),
    PRIVACY("개인정보 수집·이용 동의"),
    REVIEW_EXPOSURE("리뷰 노출 동의"),
    FEE_NOTICE("수수료 안내 동의"),
    MARKETING("마케팅 수신 동의");

    private final String label;
}
