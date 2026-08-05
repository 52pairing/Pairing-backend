package com.pairing.auth.application.port;

import java.time.Duration;

/** IP 기준 비정상 로그인 시도 차단. */
public interface LoginAttemptPort {

    boolean isBlocked(String ip);

    Duration blockRemaining(String ip);

    /** 실패를 기록하고, 상한에 도달하면 해당 IP를 차단한다. */
    void recordFailure(String ip, int maxFailure, Duration window, Duration blockDuration);

    void clearFailure(String ip);
}
