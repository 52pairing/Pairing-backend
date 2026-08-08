package com.pairing.settlement.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode {

    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ST_001", "정산 내역을 찾을 수 없습니다."),
    NOT_PAYER(HttpStatus.FORBIDDEN, "ST_002", "본인이 납부할 정산이 아닙니다."),
    NOT_PAYABLE(HttpStatus.BAD_REQUEST, "ST_003", "이미 결제되었거나 결제할 수 없는 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
