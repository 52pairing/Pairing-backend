package com.pairing.template_server.global.security;

import com.pairing.template_server.global.exception.ErrorResponseWriter;
import com.pairing.template_server.global.exception.GlobalErrorCode;
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
import java.util.Set;

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

    // 로그인/가입/토큰재발급처럼 "기존 세션 상태와 무관하게 항상 동작해야 하는" 엔드포인트.
    // 브라우저에 남아있는 낡은 토큰 쿠키 때문에 신규 로그인 시도 자체가 401로 막히는 것을 방지한다.
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_AUTH_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = resolveToken(request);

            if (token != null) {
                // validateToken(boolean)을 쓰면 만료/위조 사유가 사라져 아래 catch가 죽은 코드가 된다.
                // 클라이언트가 "재발급하면 되는 상황"과 "다시 로그인해야 하는 상황"을 구분할 수 있어야 한다.
                Claims claims = globalJwtProvider.parseClaims(token);
                String subject = claims.getSubject();
                String role = claims.get("role", String.class);

                var authorities = StringUtils.hasText(role)
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : List.<SimpleGrantedAuthority>of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            log.warn("토큰 만료: {}", e.getMessage());
            errorResponseWriter.write(response, GlobalErrorCode.TOKEN_EXPIRED);
            return;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 토큰: {}", e.getMessage());
            errorResponseWriter.write(response, GlobalErrorCode.TOKEN_INVALID);
            return;

        } catch (Exception e) {
            log.error("JWT 인증 처리 중 서버 에러 발생: {}", e.getMessage(), e);
            errorResponseWriter.write(response, GlobalErrorCode.SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization: Bearer {token}
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        // 2. accessToken 쿠키
        //    값이 빈 쿠키가 남아있는 경우가 있어 hasText로 걸러낸다.
        //    (빈 값을 그대로 넘기면 파싱 예외 → 공개 API 호출까지 401로 막힌다)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (GlobalJwtProvider.ACCESS_TOKEN_COOKIE.equals(cookie.getName())
                        && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
