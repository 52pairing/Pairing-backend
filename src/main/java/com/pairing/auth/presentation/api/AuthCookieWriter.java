package com.pairing.auth.presentation.api;

import com.pairing.auth.application.result.LoginResult;
import com.pairing.global.security.GlobalJwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 토큰을 HttpOnly 쿠키로 실어 보낸다.
 *
 * <p>응답 본문에 토큰을 넣으면 XSS로 탈취될 수 있어 쿠키로만 내려준다.
 * 쿠키 속성(secure/sameSite/domain)은 GlobalJwtProvider가 환경에 맞게 정한다.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieWriter {

    private final GlobalJwtProvider globalJwtProvider;

    public void write(HttpServletResponse response, LoginResult result) {
        add(response, globalJwtProvider.createCookie(
                GlobalJwtProvider.ACCESS_TOKEN_COOKIE, result.accessToken()));
        add(response, globalJwtProvider.createCookie(
                GlobalJwtProvider.REFRESH_TOKEN_COOKIE, result.refreshToken()));
    }

    /**
     * 인증 쿠키를 만료시킨다.
     *
     * <p>구현은 {@link GlobalJwtProvider#expireAuthCookies}에 있다. 시큐리티 필터도 같은 일을
     * 해야 하는데 필터(global)가 이 클래스(auth.presentation)를 참조할 수는 없어서,
     * 만료 로직 자체는 global 쪽에 두고 여기서는 위임만 한다. 두 곳에 복사해 두면
     * 한쪽 쿠키 속성만 바뀌어 "지운 줄 알았는데 안 지워지는" 상태가 만들어진다.
     */
    public void clear(HttpServletResponse response) {
        globalJwtProvider.expireAuthCookies(response);
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
