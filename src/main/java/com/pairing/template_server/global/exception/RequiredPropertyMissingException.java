package com.pairing.template_server.global.exception;

import lombok.Getter;

/**
 * 필수 설정값이 없거나 형식이 잘못되어 기동할 수 없을 때 던진다.
 *
 * <p>이 예외는 {@link RequiredPropertyFailureAnalyzer}가 잡아서
 * "APPLICATION FAILED TO START" 블록에 원인과 해결 방법을 출력한다.
 * 스택트레이스 깊숙한 곳에 원인이 묻히지 않게 하는 것이 목적이다.
 */
@Getter
public class RequiredPropertyMissingException extends RuntimeException {

    /** 설정 키 (예: jwt.secret-key) */
    private final String propertyName;

    /** 주입해야 하는 환경변수명 (예: JWT_SECRET_KEY) */
    private final String environmentVariable;

    /** 해결 방법 안내. 여러 줄이면 개선 조치로 그대로 출력된다. */
    private final String action;

    public RequiredPropertyMissingException(String propertyName,
                                           String environmentVariable,
                                           String reason,
                                           String action) {
        super(reason);
        this.propertyName = propertyName;
        this.environmentVariable = environmentVariable;
        this.action = action;
    }
}
