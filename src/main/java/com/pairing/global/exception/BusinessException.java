package com.pairing.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public BusinessException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 에러 코드의 고정 문구 대신 상황에 맞는 문구를 내보낼 때 쓴다.
     *
     * <p>예: IP 차단은 "언제부터 다시 되는지"가 사용자에게 필요한데 그 시각은 요청마다 다르다.
     * errorCode 는 그대로 유지되므로 프론트의 분기 로직은 영향을 받지 않는다.
     */
    public BusinessException(BaseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
