package com.pairing.contract.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 계약 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum ContractErrorCode implements BaseErrorCode {

    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CT_001", "계약을 찾을 수 없습니다."),
    NOT_CONTRACT_PARTY(HttpStatus.FORBIDDEN, "CT_002", "이 계약의 당사자가 아닙니다."),
    INVALID_CONTRACT_STATUS(HttpStatus.BAD_REQUEST, "CT_003", "현재 상태에서는 처리할 수 없습니다."),
    ALREADY_SIGNED(HttpStatus.BAD_REQUEST, "CT_004", "이미 서명한 계약입니다."),
    SIGNATURE_NOT_FOUND(HttpStatus.NOT_FOUND, "CT_005", "서명 대상을 찾을 수 없습니다."),
    INVALID_CONTRACT_FIELD(HttpStatus.BAD_REQUEST, "CT_006", "계약 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
