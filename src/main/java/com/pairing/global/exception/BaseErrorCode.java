package com.pairing.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러코드 Enum이 구현하는 공통 계약.
 * 이 인터페이스를 구현해두면 CommonExceptionAdvice와 Swagger 문서화가 자동으로 동작한다.
 */
public interface BaseErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
