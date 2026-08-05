package com.pairing.auth.presentation.api.response;

import com.pairing.auth.application.result.SocialAuthResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소셜 콜백 응답.
 *
 * <p>status가 LOGIN이면 쿠키가 함께 내려가 로그인이 끝난 상태다.
 * SIGNUP_REQUIRED면 signUpTicket과 prefill 값을 들고 추가 정보 입력 화면으로 이동한다.
 */
@Schema(description = "소셜 로그인 콜백 응답")
public record SocialAuthResponse(

        @Schema(description = "LOGIN 또는 SIGNUP_REQUIRED", example = "SIGNUP_REQUIRED")
        String status,

        @Schema(description = "로그인 정보. status가 LOGIN일 때만 채워진다.")
        LoginResponse login,

        @Schema(description = "가입 티켓. status가 SIGNUP_REQUIRED일 때만 채워진다.")
        String signUpTicket,

        @Schema(description = "공급자가 준 이메일(수정 불가)", example = "user@gmail.com")
        String email,

        @Schema(description = "공급자가 준 이름(수정 가능)", example = "홍길동")
        String name
) {

    public static SocialAuthResponse from(SocialAuthResult result) {
        if (result.status() == SocialAuthResult.Status.LOGIN) {
            return new SocialAuthResponse(
                    result.status().name(),
                    LoginResponse.from(result.loginResult()),
                    null, null, null);
        }

        return new SocialAuthResponse(
                result.status().name(),
                null,
                result.signUpTicket(),
                result.email(),
                result.name());
    }
}
