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
    PASSWORD_CHANGE("비밀번호 변경"),

    /**
     * 마이페이지 &gt; 결제수단 탭. <b>조회부터</b> 요구한다.
     *
     * <p>마스킹해서 내려도 은행명·예금주·끝 4자리가 함께 보여, 계정을 잠깐 빌린 사람에게 단서가 된다.
     *
     * <p>{@code PROFILE_UPDATE} 를 재사용하지 않는다. 사용자가 프로필 화면에서 받은 코드로
     * 결제수단까지 열리면 안 된다. 위 문단의 분리 원칙 그대로다.
     */
    PAYMENT_METHOD("결제수단 확인");

    private final String label;
}
