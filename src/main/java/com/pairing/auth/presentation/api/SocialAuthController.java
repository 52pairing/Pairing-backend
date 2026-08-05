package com.pairing.auth.presentation.api;

import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.command.SocialCallbackCommand;
import com.pairing.auth.application.result.AuthorizeUrlResult;
import com.pairing.auth.application.result.SocialAuthResult;
import com.pairing.auth.application.usecase.SocialAuthUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.presentation.api.request.SocialCallbackRequest;
import com.pairing.auth.presentation.api.response.AuthorizeUrlResponse;
import com.pairing.auth.presentation.api.response.SocialAuthResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 (프리랜서 전용).
 *
 * <p>리다이렉트는 프론트가 처리하고, 서버는 인가 URL 발급과 콜백 처리만 담당한다.
 * 콜백 결과가 SIGNUP_REQUIRED면 가입 티켓을 들고 추가 정보 입력 화면으로 이동한다.
 */
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
@Tag(name = "01. Auth - Social", description = "소셜 로그인 API")
public class SocialAuthController {

    private final SocialAuthUseCase socialAuthUseCase;
    private final AuthCookieWriter authCookieWriter;

    @GetMapping("/{provider}/authorize")
    @Operation(summary = "소셜 인가 URL 발급", description = "provider는 kakao 또는 google 입니다.")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"SOCIAL_AUTH_FAILED"})
    public ResponseEntity<ApiResponse<AuthorizeUrlResponse>> authorize(
            @PathVariable String provider,
            @RequestParam(required = false) String returnUrl
    ) {
        AuthorizeUrlResult result = socialAuthUseCase.authorizeUrl(toProvider(provider), returnUrl);

        return ResponseEntity.ok(ApiResponse.success("AUTHORIZE_URL_ISSUED", "인가 URL을 발급했습니다.",
                new AuthorizeUrlResponse(result.authorizeUrl(), result.state())));
    }

    @PostMapping("/{provider}/callback")
    @Operation(summary = "소셜 콜백 처리",
            description = "이미 연동된 계정이면 로그인 쿠키가 발급되고, 아니면 가입 티켓을 반환합니다.")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "SOCIAL_AUTH_FAILED", "DUPLICATED_EMAIL", "ACCOUNT_LOCKED", "ACCOUNT_SUSPENDED", "LOGIN_FAILED"})
    public ResponseEntity<ApiResponse<SocialAuthResponse>> callback(
            @PathVariable String provider,
            @Valid @RequestBody SocialCallbackRequest request,
            HttpServletResponse response
    ) {
        SocialAuthResult result = socialAuthUseCase.callback(
                new SocialCallbackCommand(toProvider(provider), request.code(), request.state()));

        if (result.status() == SocialAuthResult.Status.LOGIN) {
            authCookieWriter.write(response, result.loginResult());
        }

        return ResponseEntity.ok(ApiResponse.success("SOCIAL_AUTH_PROCESSED", "소셜 인증을 처리했습니다.",
                SocialAuthResponse.from(result)));
    }

    /** 경로에는 소문자(kakao/google)가 오므로 enum 변환을 직접 한다. 기본 변환기는 대소문자를 구분한다. */
    private SocialProvider toProvider(String provider) {
        try {
            return SocialProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }
}
