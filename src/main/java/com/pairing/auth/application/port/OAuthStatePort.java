package com.pairing.auth.application.port;

import java.time.Duration;

/** 소셜 인가 요청의 state 저장소. 콜백에서 한 번만 쓰고 지운다(CSRF 방지). */
public interface OAuthStatePort {

    void save(String state, String returnUrl, Duration ttl);

    /** state가 존재하면 삭제하고 true를 반환한다. 없으면 false. */
    boolean consume(String state);
}
