package com.pairing.template_server.global.websocket;

import com.pairing.template_server.global.security.GlobalJwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * STOMP 핸드셰이크에서 JWT를 검증하고 세션 속성에 사용자 정보를 심는다.
 *
 * <p>WebSocket 프레임에는 시큐리티 필터가 걸리지 않으므로, 인증은 <b>핸드셰이크 시점에 한 번</b> 수행하고
 * 결과를 세션 속성에 저장해 이후 메시지에서 재사용한다. 따라서 핸드셰이크 경로는 시큐리티에서 permitAll로
 * 열어두고(GlobalSecurityConfig), 실제 인증은 이 인터셉터가 담당한다.
 *
 * <p>토큰은 쿼리 파라미터가 아니라 {@code accessToken} <b>쿠키</b>에서 읽는다. 쿼리 파라미터로 받으면
 * 접속 URL이 액세스 로그와 Referer에 그대로 남는다.
 *
 * <p>{@link WebSocketUserPort} 구현체가 등록되어 있으면 사용자 PK까지 확인하고, 조회에 실패하면
 * 핸드셰이크를 거절한다. 구현체가 없으면(스켈레톤 기본 상태) subject만 담고 통과시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final GlobalJwtProvider globalJwtProvider;

    /** 사용자 도메인이 어댑터를 등록하지 않았을 수 있으므로 지연 조회한다. */
    private final ObjectProvider<WebSocketUserPort> webSocketUserPortProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveTokenFromCookie(request);
        if (token == null) {
            log.warn("[WebSocket] 핸드셰이크 거절: accessToken 쿠키 없음");
            return false;
        }

        try {
            // parseClaims는 만료/위조 사유를 예외로 구분해준다. validateToken(boolean)만 쓰면 사유가 사라진다.
            Claims claims = globalJwtProvider.parseClaims(token);
            String principal = claims.getSubject();
            if (principal == null || principal.isBlank()) {
                log.warn("[WebSocket] 핸드셰이크 거절: 토큰에 subject 없음");
                return false;
            }

            attributes.put(WebSocketSessionAttributes.PRINCIPAL, principal);
            attributes.put(WebSocketSessionAttributes.ROLE, claims.get("role", String.class));

            WebSocketUserPort userPort = webSocketUserPortProvider.getIfAvailable();
            if (userPort == null) {
                // 스켈레톤 기본 상태. userId가 필요한 기능은 어댑터를 등록해야 동작한다.
                log.debug("[WebSocket] WebSocketUserPort 구현체가 없어 userId 없이 접속 허용: principal={}", principal);
                return true;
            }

            Long userId = userPort.findUserIdByPrincipal(principal).orElse(null);
            if (userId == null) {
                log.warn("[WebSocket] 핸드셰이크 거절: 사용자를 찾을 수 없음 principal={}", principal);
                return false;
            }

            attributes.put(WebSocketSessionAttributes.USER_ID, userId);
            return true;

        } catch (Exception e) {
            // 만료·위조·서명 불일치 모두 여기로 온다. 접속을 거절하는 동작은 같으므로 사유만 남긴다.
            log.warn("[WebSocket] 핸드셰이크 거절: 토큰 검증 실패 ({})", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 후처리할 것이 없다.
    }

    private String resolveTokenFromCookie(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (GlobalJwtProvider.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
