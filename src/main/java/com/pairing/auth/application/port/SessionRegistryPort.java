package com.pairing.auth.application.port;

import java.time.Duration;

/** 계정별 최신 세션 ID 저장소. 액세스 토큰의 sid 클레임과 대조해 중복 로그인을 끊는다. */
public interface SessionRegistryPort {

    void register(Long accountId, String sessionId, Duration ttl);

    boolean isAlive(Long accountId, String sessionId);

    void clear(Long accountId);
}
