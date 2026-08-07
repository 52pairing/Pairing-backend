package com.pairing.freelancer.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FreelancerErrorCode implements BaseErrorCode {

    CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "FR_001", "등록된 조건이 없습니다."),
    INVALID_CONDITION_FIELD(HttpStatus.BAD_REQUEST, "FR_002", "조건 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
