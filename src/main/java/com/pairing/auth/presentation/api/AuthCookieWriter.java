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

    public void clear(HttpServletResponse response) {
        add(response, globalJwtProvider.deleteCookie(GlobalJwtProvider.ACCESS_TOKEN_COOKIE));
        add(response, globalJwtProvider.deleteCookie(GlobalJwtProvider.REFRESH_TOKEN_COOKIE));
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
