package com.pairing.terms.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements BaseErrorCode {

    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TM_001", "약관을 찾을 수 없습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TM_002", "필수 약관에 동의해야 합니다."),
    UNKNOWN_TERMS_INCLUDED(HttpStatus.BAD_REQUEST, "TM_003", "존재하지 않는 약관이 포함되어 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
