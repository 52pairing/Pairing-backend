package com.pairing.auth.application.port;

import java.time.Duration;
import java.util.Optional;

/** 비밀번호 재설정 링크 토큰. 3분 안에 링크를 열지 않으면 사라진다. */
public interface PasswordResetTokenPort {

    void save(String token, Long accountId, Duration ttl);

    Optional<Long> findAccountId(String token);

    void delete(String token);
}
