package com.pairing.auth.application.service;

import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.global.security.GlobalJwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 토큰 발급을 한 곳에 모은다.
 *
 * <p>로그인, 소셜 로그인, 소셜 가입, 재발급 네 곳에서 같은 절차가 필요하다.
 * 흩어지면 "Redis 저장을 빠뜨린 경로" 하나 때문에 중복 로그인 차단이 뚫린다.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {

    private final GlobalJwtProvider globalJwtProvider;
    private final TokenStorePort tokenStorePort;
    private final SessionRegistryPort sessionRegistryPort;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /** 새 세션을 열고 토큰을 발급한다. 이전 기기의 세션은 이 시점에 무효가 된다. */
    public LoginResult issue(Account account) {
        return issue(account, UUID.randomUUID().toString());
    }

    /** 기존 세션 ID를 유지한 채 토큰만 새로 발급한다. (재발급 경로) */
    public LoginResult issue(Account account, String sessionId) {
        String subject = String.valueOf(account.getId());
        Duration ttl = Duration.ofMillis(refreshTokenExpiration);

        String accessToken = globalJwtProvider.createAccessToken(subject, account.getRole().name(), sessionId);
        String refreshToken = globalJwtProvider.createRefreshToken(subject, sessionId);

        sessionRegistryPort.register(account.getId(), sessionId, ttl);
        tokenStorePort.save(account.getId(), refreshToken, ttl);

        return new LoginResult(
                account.getId(),
                account.getRole().name(),
                account.getName(),
                account.isTempPassword(),
                accessToken,
                refreshToken
        );
    }
}
