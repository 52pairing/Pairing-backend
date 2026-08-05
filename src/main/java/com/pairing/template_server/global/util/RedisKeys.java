package com.pairing.template_server.global.util;

/**
 * Redis 키 접두사를 한 곳에 모아둔다.
 *
 * <p>여러 클래스에 문자열 리터럴로 흩어지면 오타로 read/write 키가 어긋나도 컴파일 에러가 나지 않는다.
 * 새 키를 추가할 때는 반드시 여기에 상수로 선언하고, 값은 {@code "PREFIX:"} 형태로 콜론을 붙인다.
 */
public final class RedisKeys {

    /** 리프레시 토큰 저장 (RT:{subject}) */
    public static final String REFRESH_TOKEN_PREFIX = "RT:";

    /** 이메일/휴대폰 인증코드 저장 (AUTH_CODE:{target}) */
    public static final String AUTH_CODE_PREFIX = "AUTH_CODE:";

    /** 인증 성공 마커 (AUTH_SUCCESS:{target}) */
    public static final String AUTH_SUCCESS_PREFIX = "AUTH_SUCCESS:";

    /** WebSocket 접속 중인 사용자 마커 (ONLINE:{userId}) */
    public static final String ONLINE_PREFIX = "ONLINE:";

    private RedisKeys() {
        throw new IllegalStateException("Utility class");
    }
}
