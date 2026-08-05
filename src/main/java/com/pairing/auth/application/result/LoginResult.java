package com.pairing.auth.application.result;

/**
 * 로그인 결과.
 *
 * <p>토큰은 컨트롤러가 HttpOnly 쿠키로 내려준다. 응답 본문에 넣지 않는다.
 */
public record LoginResult(
        Long accountId,
        String role,
        String name,
        boolean tempPassword,
        String accessToken,
        String refreshToken
) {
}
