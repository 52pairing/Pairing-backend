package com.pairing.grade.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GradeErrorCode implements BaseErrorCode {

    INVALID_ROLE(HttpStatus.BAD_REQUEST, "GR_001", "등급은 클라이언트/프리랜서만 조회할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
