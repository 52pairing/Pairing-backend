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
    SOCIAL_NOT_ALLOWED_FOR_ROLE(HttpStatus.BAD_REQUEST, "AC_005", "해당 역할은 소셜 계정을 사용할 수 없습니다."),
    UNKNOWN_BANK_CODE(HttpStatus.BAD_REQUEST, "AC_006", "지원하지 않는 은행입니다."),
    PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "AC_007", "등록된 결제수단이 없습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "AC_008", "이미 탈퇴한 계정입니다."),
    /** 확인 문구 불일치. 되돌릴 수 없는 작업이라 서버에서도 한 번 더 본다. */
    WITHDRAW_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "AC_009", "확인 문구가 일치하지 않습니다."),
    WITHDRAW_BLOCKED_BY_PROJECT(HttpStatus.CONFLICT, "AC_010",
            "진행 중인 프로젝트가 있어 탈퇴할 수 없습니다. 프로젝트를 종료한 뒤 다시 시도해 주세요."),
    WITHDRAW_BLOCKED_BY_SETTLEMENT(HttpStatus.CONFLICT, "AC_011",
            "미납된 수수료가 있어 탈퇴할 수 없습니다. 결제를 완료한 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
