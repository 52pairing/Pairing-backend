package com.pairing.auth.infrastructure.security;

import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.global.security.TokenSessionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 중복 로그인 차단의 실제 판정.
 *
 * <p>global 필터가 매 요청마다 호출한다. Redis GET 한 번의 비용으로
 * "다른 기기에서 로그인되었습니다" 안내를 즉시 띄울 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenSessionValidator implements TokenSessionValidator {

    private final SessionRegistryPort sessionRegistryPort;

    @Override
    public boolean isAlive(String subject, String sessionId) {
        Long accountId;
        try {
            accountId = Long.valueOf(subject);
        } catch (NumberFormatException e) {
            // subject 가 계정 ID 형식이 아니면 우리가 발급한 토큰이 아니다.
            log.warn("토큰 subject 형식이 올바르지 않다: {}", subject);
            return false;
        }

        try {
            return sessionRegistryPort.isAlive(accountId, sessionId);
        } catch (RuntimeException e) {
            // Redis 장애로 판정을 못 하는 상황이다. 여기서 막으면 인증이 필요한 모든 API가 동시에 죽는다.
            // 토큰 서명·만료는 이미 검증됐으므로 통과시키고, 중복 로그인 차단만 최대 30분(액세스 토큰 수명) 느슨해진다.
            // 보안을 우선해 차단하려면 이 블록을 false 로 바꾼다.
            log.error("세션 검증 실패(Redis). 중복 로그인 차단을 일시적으로 건너뛴다: {}", e.getMessage());
            return true;
        }
    }
}
