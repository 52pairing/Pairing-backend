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
    INVALID_CONTRACT_FIELD(HttpStatus.BAD_REQUEST, "CT_006", "계약 정보가 올바르지 않습니다."),
    PDF_RENDER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CT_007", "계약서 파일을 만들지 못했습니다."),

    /** 수행분 산정 기준(정책 P32)이 정해지지 않아 아직 만들 수 없다. 기준이 확정되면 제거한다. */
    TERMINATION_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "CT_008", "중도 파기는 아직 지원하지 않습니다."),

    /**
     * AI 서버가 2xx 를 주면서 본문을 비웠다. 계약이 DRAFT 에 남고 스케줄러가 다시 시도한다.
     *
     * <p>화면에 뜨는 코드가 아니다. 어댑터가 "실패했다"를 호출부에 알리는 수단이라,
     * 이 값이 응답으로 나가는 경로는 없다.
     */
    DRAFT_TEXT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "CT_009", "계약서 문구를 생성하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
