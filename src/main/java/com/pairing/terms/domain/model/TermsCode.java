package com.pairing.terms.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 약관 종류. 버전은 code + version 조합으로 관리한다.
 *
 * <p>가입 화면에 뜨는 동의 항목은 세 개다.
 * 서비스 이용약관(필수) / 개인정보 수집 및 이용 동의(필수) / 마케팅 정보 수신 동의(선택).
 * 서비스 이용약관은 역할별로 내용이 달라 같은 코드로 두 행을 두고 {@code targetRole} 로 가른다.
 *
 * <p>{@code PRIVACY_POLICY} 는 동의 항목이 아니라 게시용 문서다. 가입 화면에 노출하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum TermsCode {

    SERVICE(TermsType.AGREEMENT, "서비스 이용약관 동의"),
    PRIVACY_CONSENT(TermsType.AGREEMENT, "개인정보 수집 및 이용 동의"),
    MARKETING(TermsType.AGREEMENT, "마케팅 정보 수신 동의"),

    PRIVACY_POLICY(TermsType.POLICY, "개인정보 처리방침");

    private final TermsType type;
    private final String label;
}
