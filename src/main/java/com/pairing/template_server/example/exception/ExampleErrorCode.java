package com.pairing.template_server.example.exception;

import com.pairing.template_server.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExampleErrorCode implements BaseErrorCode {

    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "EX_001", "예시 데이터를 찾을 수 없습니다."),
    INVALID_EXAMPLE_NAME(HttpStatus.BAD_REQUEST, "EX_002", "유효하지 않은 예시 이름입니다."),
    // 도메인 검증용 에러 코드 추가
    NAME_LENGTH_EXCEEDED(HttpStatus.BAD_REQUEST, "EX_003", "이름은 2자 이상 50자 이하여야 합니다."),
    CANNOT_DEACTIVATE_NEW_EXAMPLE(HttpStatus.BAD_REQUEST, "EX_004", "생성 직후의 예시는 비활성화할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}