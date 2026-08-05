package com.pairing.global.util;

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

    /** 단일 세션 판정용 세션 ID (SESSION:{accountId}) */
    public static final String SESSION_PREFIX = "SESSION:";

    /** 이메일 인증코드 발송 횟수 (EMAIL_SEND:{email}) */
    public static final String EMAIL_SEND_COUNT_PREFIX = "EMAIL_SEND:";

    /** IP 기준 로그인 실패 횟수 (LOGIN_FAIL_IP:{ip}) */
    public static final String LOGIN_FAIL_IP_PREFIX = "LOGIN_FAIL_IP:";

    /** IP 차단 마커 (LOGIN_BLOCK_IP:{ip}) */
    public static final String LOGIN_BLOCK_IP_PREFIX = "LOGIN_BLOCK_IP:";

    /** 비밀번호 재설정 링크 토큰 (PW_RESET:{token}) */
    public static final String PASSWORD_RESET_PREFIX = "PW_RESET:";

    /** 소셜 가입 티켓 (SIGNUP_TICKET:{ticket}) */
    public static final String SIGNUP_TICKET_PREFIX = "SIGNUP_TICKET:";

    /** 소셜 인가 요청 state (OAUTH_STATE:{state}) */
    public static final String OAUTH_STATE_PREFIX = "OAUTH_STATE:";

    /** 회원 정지 상태 (SUSPEND:{accountId}) */
    public static final String SUSPEND_PREFIX = "SUSPEND:";

    private RedisKeys() {
        throw new IllegalStateException("Utility class");
    }
}
