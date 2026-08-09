package com.pairing.support.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InquiryErrorCode implements BaseErrorCode {

    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "IQ_001", "문의를 찾을 수 없습니다."),
    INQUIRY_FORBIDDEN(HttpStatus.FORBIDDEN, "IQ_002", "본인의 문의만 열람할 수 있습니다."),
    INVALID_INQUIRY_FIELD(HttpStatus.BAD_REQUEST, "IQ_003", "문의 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
