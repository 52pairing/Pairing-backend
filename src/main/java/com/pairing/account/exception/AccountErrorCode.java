package com.pairing.account.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements BaseErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AC_001", "계정을 찾을 수 없습니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "AC_002", "프로필 정보를 찾을 수 없습니다."),
    INVALID_ACCOUNT_STATE(HttpStatus.BAD_REQUEST, "AC_003", "현재 상태에서는 처리할 수 없습니다."),
    INVALID_ACCOUNT_FIELD(HttpStatus.BAD_REQUEST, "AC_004", "계정 정보가 올바르지 않습니다."),
    SOCIAL_NOT_ALLOWED_FOR_ROLE(HttpStatus.BAD_REQUEST, "AC_005", "해당 역할은 소셜 계정을 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
