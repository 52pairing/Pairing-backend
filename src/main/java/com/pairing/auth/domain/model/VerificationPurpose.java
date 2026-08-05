package com.pairing.auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이메일 인증 용도.
 *
 * <p>비밀번호 초기화는 코드가 아니라 링크 방식이라 여기에 포함하지 않는다. (스키마 v12 결정)
 */
@Getter
@RequiredArgsConstructor
public enum VerificationPurpose {

    SIGNUP("회원가입"),
    UNLOCK("계정 잠금 해제"),
    PROFILE_UPDATE("정보 변경");

    private final String label;
}
