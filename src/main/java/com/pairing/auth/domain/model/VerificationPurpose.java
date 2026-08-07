package com.pairing.auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이메일 인증 용도.
 *
 * <p>로그인 전 "비밀번호 찾기"는 코드가 아니라 링크 방식이라 여기에 없다.
 * 로그인 후 마이페이지에서 하는 "비밀번호 변경"은 코드 방식이라 {@code PASSWORD_CHANGE} 로 들어간다.
 * 둘을 같은 용도로 묶으면 한쪽 코드로 다른 쪽을 통과할 수 있으므로 분리한다.
 */
@Getter
@RequiredArgsConstructor
public enum VerificationPurpose {

    SIGNUP("회원가입"),
    UNLOCK("계정 잠금 해제"),
    PROFILE_UPDATE("정보 변경"),
    PASSWORD_CHANGE("비밀번호 변경");

    private final String label;
}
