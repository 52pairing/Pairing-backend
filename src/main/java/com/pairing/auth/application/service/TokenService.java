package com.pairing.auth.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.application.usecase.TokenUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.security.GlobalJwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 토큰 재발급과 로그아웃.
 *
 * <p>재발급은 저장된 리프레시 토큰과 값이 같을 때만 허용한다. 다른 기기에서 로그인하면
 * 저장값이 덮어써지므로 이전 기기의 재발급이 자동으로 막힌다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TokenService implements TokenUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final TokenStorePort tokenStorePort;
    private final SessionRegistryPort sessionRegistryPort;
    private final AuthTokenIssuer authTokenIssuer;
    private final GlobalJwtProvider globalJwtProvider;

    @Override
    public LoginResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims = globalJwtProvider.parseClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            // 만료와 위조를 구분해도 사용자가 할 일은 재로그인 하나뿐이라 같은 코드로 응답한다.
            log.warn("리프레시 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long accountId = parseAccountId(claims.getSubject());
        String sessionId = claims.get(GlobalJwtProvider.SESSION_ID_CLAIM, String.class);

        Optional<String> stored = tokenStorePort.find(accountId);
        if (stored.isEmpty()) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!stored.get().equals(refreshToken)) {
            // 값이 다르다 = 다른 기기가 로그인해 저장값을 덮어썼다.
            throw new BusinessException(AuthErrorCode.SESSION_TERMINATED);
        }

        Account account = accountQueryUseCase.getById(accountId);

        // 같은 기기의 갱신이므로 세션 ID는 그대로 유지한다. (진행 중이던 요청이 끊기지 않도록)
        // sid가 없는 토큰(단일 세션 도입 전 발급분)은 이번 재발급에서 새 세션을 연다.
        return sessionId == null || sessionId.isBlank()
                ? authTokenIssuer.issue(account)
                : authTokenIssuer.issue(account, sessionId);
    }

    // 서명은 유효하지만 subject 가 계정 ID 형식이 아닌 토큰은 우리가 발급한 것이 아니다.
    // 여기서 걸러내지 않으면 NumberFormatException 이 500으로 나간다.
    private Long parseAccountId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    @Override
    public void logout(Long accountId) {
        tokenStorePort.delete(accountId);
        sessionRegistryPort.clear(accountId);
    }
}
