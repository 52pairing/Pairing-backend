package com.pairing.global.security;

import com.pairing.global.exception.ErrorResponseWriter;
import com.pairing.global.exception.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 요청마다 JWT를 검증해 SecurityContext에 인증 정보를 채운다.
 * 토큰은 Authorization 헤더(Bearer) 또는 accessToken 쿠키에서 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GlobalJwtProvider globalJwtProvider;
    private final ErrorResponseWriter errorResponseWriter;

    // 단일 세션 검사 구현체. auth 도메인이 없으면(또는 아직 없을 때) 비어 있고 검사를 건너뛴다.
    private final Optional<TokenSessionValidator> tokenSessionValidator;

    // 로그인/가입/토큰재발급/공개 조회처럼 "기존 세션 상태와 무관하게 항상 동작해야 하는" 엔드포인트.
    // 브라우저에 남아있는 낡은 토큰 쿠키 때문에 신규 로그인 시도 자체가 401로 막히는 것을 방지한다.
    //
    // 정확 일치가 아니라 접두사로 비교한다. /api/v1/auth/signup/client 처럼 하위 경로가 생겨도
    // 목록에 다시 등록하지 않으면 낡은 쿠키 때문에 가입 요청이 401로 막히기 때문이다.
    // 반대로 인증이 필요한 auth 경로(PATCH /api/v1/auth/password, GET /api/v1/auth/me)는
    // 이 목록에 걸리지 않도록 재설정 경로를 각각 적어 둔다.
    private static final List<String> PUBLIC_AUTH_PATH_PREFIXES = List.of(
            // 비로그인 조회. 만료된 쿠키가 남아 있다고 해서 가입 화면의 약관/선택목록이 막히면 안 된다.
            "/api/v1/meta",
            "/api/v1/terms",
            "/api/v1/home",
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/social",
            "/api/v1/auth/email-verifications",
            "/api/v1/auth/exists",
            "/api/v1/auth/find-email",
            "/api/v1/auth/unlock",
            "/api/v1/auth/password/reset-requests",
            "/api/v1/auth/password/reset-confirm"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return PUBLIC_AUTH_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 쿠키에서 온 토큰인지 기억해 둔다. 아래에서 "못 쓰는 쿠키를 지워도 되는지" 판단에 쓴다.
        ResolvedToken resolved = resolveToken(request);

        try {
            if (resolved != null) {
                // validateToken(boolean)을 쓰면 만료/위조 사유가 사라져 아래 catch가 죽은 코드가 된다.
                // 클라이언트가 "재발급하면 되는 상황"과 "다시 로그인해야 하는 상황"을 구분할 수 있어야 한다.
                Claims claims = globalJwtProvider.parseClaims(resolved.value());
                String subject = claims.getSubject();
                String role = claims.get("role", String.class);
                String sessionId = claims.get(GlobalJwtProvider.SESSION_ID_CLAIM, String.class);

                // 중복 로그인 차단. 마지막 로그인으로 발급된 토큰이 아니면 여기서 끊는다.
                // 만료(GLOBAL_009)와 구분되는 코드를 내려야 프론트가 "다른 기기 로그인" 모달을 띄울 수 있다.
                if (!isSessionAlive(subject, sessionId)) {
                    log.warn("종료된 세션의 토큰: subject={}", subject);
                    expireCookiesIfFromCookie(response, resolved);
                    errorResponseWriter.write(response, GlobalErrorCode.SESSION_TERMINATED);
                    return;
                }

                var authorities = StringUtils.hasText(role)
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : List.<SimpleGrantedAuthority>of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            // 여기서는 쿠키를 지우지 않는다. 액세스 토큰 만료는 리프레시 토큰으로 복구되는 정상 흐름이고,
            // 지워 버리면 재발급에 쓸 refreshToken 쿠키까지 함께 날아가 멀쩡한 로그인이 끊긴다.
            log.warn("토큰 만료: {}", e.getMessage());
            errorResponseWriter.write(response, GlobalErrorCode.TOKEN_EXPIRED);
            return;

        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·형식 오류는 시간이 지나도 절대 유효해지지 않는다. 쿠키에 남겨두면
            // 모든 요청이 이 401을 받으므로 세션 종료와 똑같이 만료시킨다.
            log.warn("유효하지 않은 토큰: {}", e.getMessage());
            expireCookiesIfFromCookie(response, resolved);
            errorResponseWriter.write(response, GlobalErrorCode.TOKEN_INVALID);
            return;

        } catch (Exception e) {
            log.error("JWT 인증 처리 중 서버 에러 발생: {}", e.getMessage(), e);
            errorResponseWriter.write(response, GlobalErrorCode.SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSessionAlive(String subject, String sessionId) {
        // 검사기가 없거나 sid가 없는 토큰(단일 세션 도입 전 발급분)은 통과시킨다.
        if (tokenSessionValidator.isEmpty() || !StringUtils.hasText(sessionId)) {
            return true;
        }
        return tokenSessionValidator.get().isAlive(subject, sessionId);
    }

    /**
     * 못 쓰는 토큰이 쿠키에서 왔을 때만 쿠키를 만료시킨다.
     *
     * <p>Authorization 헤더로 들어온 토큰까지 쿠키를 지우면, Swagger나 스크립트에서 낡은
     * Bearer 토큰 한 번 잘못 보낸 것 때문에 같은 브라우저의 멀쩡한 로그인 쿠키가 날아간다.
     * 헤더 토큰은 쿠키와 무관하므로 건드리지 않는다.
     */
    private void expireCookiesIfFromCookie(HttpServletResponse response, ResolvedToken resolved) {
        if (resolved != null && resolved.fromCookie()) {
            globalJwtProvider.expireAuthCookies(response);
        }
    }

    /** 토큰과 그 출처. 출처를 알아야 "이 쿠키를 지워도 되는지"를 판단할 수 있다. */
    private record ResolvedToken(String value, boolean fromCookie) {
    }

    private ResolvedToken resolveToken(HttpServletRequest request) {
        // 1. Authorization: Bearer {token}
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return new ResolvedToken(header.substring(BEARER_PREFIX.length()), false);
        }

        // 2. accessToken 쿠키
        //    값이 빈 쿠키가 남아있는 경우가 있어 hasText로 걸러낸다.
        //    (빈 값을 그대로 넘기면 파싱 예외 → 공개 API 호출까지 401로 막힌다)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (GlobalJwtProvider.ACCESS_TOKEN_COOKIE.equals(cookie.getName())
                        && StringUtils.hasText(cookie.getValue())) {
                    return new ResolvedToken(cookie.getValue(), true);
                }
            }
        }

        return null;
    }
}
