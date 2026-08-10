package com.pairing.support.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatbotErrorCode implements BaseErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CB_001", "챗봇 세션을 찾을 수 없습니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "CB_002", "본인의 챗봇 세션만 열람할 수 있습니다."),
    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "CB_003", "오늘의 챗봇 질의 횟수를 모두 사용했습니다."),
    AI_SERVER_CALL_FAILED(HttpStatus.BAD_GATEWAY, "CB_004", "챗봇 응답 생성에 실패했습니다."),
    INVALID_MESSAGE(HttpStatus.BAD_REQUEST, "CB_005", "챗봇 메시지 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
