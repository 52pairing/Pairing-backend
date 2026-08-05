package com.pairing.auth.presentation.api;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.auth.application.command.LoginCommand;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.application.usecase.LoginUseCase;
import com.pairing.auth.application.usecase.TokenUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.presentation.api.request.LoginRequest;
import com.pairing.auth.presentation.api.response.LoginResponse;
import com.pairing.auth.presentation.api.response.MeResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.GlobalJwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 로그인 / 로그아웃 / 토큰 재발급.
 *
 * <p>토큰은 항상 HttpOnly 쿠키로 나간다. 응답 본문에는 화면 표시에 필요한 값만 담는다.
 *
 * <p>{@code /api/v1/auth/**} 는 GlobalSecurityConfig에서 permitAll 이므로,
 * 인증이 필요한 메서드에는 @PreAuthorize 를 반드시 붙인다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "01. Auth - Login", description = "로그인/로그아웃/토큰 API")
public class AuthController {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final LoginUseCase loginUseCase;
    private final TokenUseCase tokenUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final AuthCookieWriter authCookieWriter;
    private final GlobalJwtProvider globalJwtProvider;

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인",
            description = "성공 시 accessToken/refreshToken 쿠키가 발급됩니다. 이전 기기의 세션은 즉시 끊깁니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "LOGIN_FAILED", "ACCOUNT_LOCKED", "LOGIN_BLOCKED", "ACCOUNT_SUSPENDED"})
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LoginCommand command = new LoginCommand(
                loginRequest.email(),
                loginRequest.password(),
                loginRequest.role(),
                resolveClientIp(request)
        );

        LoginResult result = loginUseCase.login(command);
        authCookieWriter.write(response, result);

        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", "로그인에 성공했습니다.",
                LoginResponse.from(result)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰 재발급",
            description = "refreshToken 쿠키로 재발급합니다. 다른 기기가 로그인했다면 AU_015로 실패합니다.")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"REFRESH_TOKEN_INVALID", "SESSION_TERMINATED"})
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = tokenUseCase.reissue(resolveRefreshToken(request));
        authCookieWriter.write(response, result);

        return ResponseEntity.ok(ApiResponse.success("TOKEN_REISSUED", "토큰이 재발급되었습니다.",
                LoginResponse.from(result)));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Redis의 리프레시 토큰과 세션을 지우고 쿠키를 만료시킵니다.")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 이미 만료된 토큰으로 로그아웃을 눌러도 쿠키는 지워져야 한다. 토큰 파싱 실패를 예외로 올리지 않는다.
        resolveAccountId(request).ifPresent(tokenUseCase::logout);
        authCookieWriter.clear(response);

        return ResponseEntity.ok(ApiResponse.success("LOGOUT_SUCCESS", "로그아웃되었습니다."));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "현재 로그인 사용자 조회")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED", "TOKEN_EXPIRED"})
    public ResponseEntity<ApiResponse<MeResponse>> me(Authentication authentication) {
        Long accountId = AuthenticatedAccount.idOf(authentication);

        return ResponseEntity.ok(ApiResponse.success("ME_FOUND", "조회에 성공했습니다.",
                MeResponse.from(accountQueryUseCase.getById(accountId))));
    }

    /** 프록시 뒤에서는 remoteAddr이 프록시 IP라 X-Forwarded-For의 첫 값을 우선한다. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (GlobalJwtProvider.REFRESH_TOKEN_COOKIE.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 만료/위조 토큰이어도 로그아웃 자체는 성공해야 하므로 예외를 삼키고 빈 값을 돌려준다. */
    private Optional<Long> resolveAccountId(HttpServletRequest request) {
        String refreshToken = resolveRefreshToken(request);
        if (refreshToken == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(globalJwtProvider.getSubject(refreshToken)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
