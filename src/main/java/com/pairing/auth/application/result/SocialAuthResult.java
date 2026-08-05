package com.pairing.auth.application.result;

/**
 * 소셜 콜백 결과.
 *
 * <p>이미 연동된 계정이면 바로 로그인(LOGIN), 아니면 추가 정보 입력이 필요하다(SIGNUP_REQUIRED).
 */
public record SocialAuthResult(
        Status status,
        LoginResult loginResult,
        String signUpTicket,
        String email,
        String name
) {

    public enum Status {
        LOGIN,
        SIGNUP_REQUIRED
    }

    public static SocialAuthResult login(LoginResult loginResult) {
        return new SocialAuthResult(Status.LOGIN, loginResult, null, null, null);
    }

    public static SocialAuthResult signUpRequired(String signUpTicket, String email, String name) {
        return new SocialAuthResult(Status.SIGNUP_REQUIRED, null, signUpTicket, email, name);
    }
}
