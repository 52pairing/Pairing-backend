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
    SITE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RV_003", "사이트 리뷰를 찾을 수 없습니다."),

    /** 성공보수 수수료 결제까지 끝나야 리뷰를 쓸 수 있다. (P51) */
    NOT_REVIEWABLE_YET(HttpStatus.CONFLICT, "RV_004", "대금 지급이 완료된 후에 리뷰를 작성할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
