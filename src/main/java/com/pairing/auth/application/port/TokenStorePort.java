package com.pairing.auth.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * 리프레시 토큰 저장소.
 *
 * <p>계정당 한 개만 보관한다. 새 로그인이 값을 덮어쓰는 순간 이전 기기의 재발급이 실패한다.
 */
public interface TokenStorePort {

    void save(Long accountId, String refreshToken, Duration ttl);

    Optional<String> find(Long accountId);

    void delete(Long accountId);
}
