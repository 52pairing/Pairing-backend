package com.pairing.review.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements BaseErrorCode {

    ALREADY_REVIEWED(HttpStatus.CONFLICT, "RV_001", "이미 이 계약에 대한 리뷰를 작성했습니다."),
    INVALID_REVIEW_FIELD(HttpStatus.BAD_REQUEST, "RV_002", "리뷰 정보가 올바르지 않습니다."),
    SITE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RV_003", "사이트 리뷰를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
