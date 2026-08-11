package com.pairing.negotiation.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 협상 도메인 에러코드. api-spec 의 NG_* 와 일치한다. */
@Getter
@RequiredArgsConstructor
public enum NegotiationErrorCode implements BaseErrorCode {

    NEGOTIATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NG_001", "협상을 찾을 수 없습니다."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "NG_002", "협상 당사자가 아닙니다."),
    FLOOR_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "NG_003", "마지노선을 이미 제출했습니다."),
    INVALID_CONDITION(HttpStatus.BAD_REQUEST, "NG_004", "조건 타입이 일치하지 않거나 누락되었습니다."),
    FLOOR_EXCEEDS_BUDGET(HttpStatus.BAD_REQUEST, "NG_005", "마지노선이 예산 상한을 초과했습니다."),
    CONDITION_ALREADY_LOCKED(HttpStatus.CONFLICT, "NG_006", "이미 합의된 조건입니다."),
    NO_PROPOSAL_TO_RESPOND(HttpStatus.BAD_REQUEST, "NG_007", "응답할 제안이 없는 조건입니다."),
    NOT_IN_PROGRESS(HttpStatus.CONFLICT, "NG_008", "진행 중인 협상이 아닙니다."),
    NOT_AGREED(HttpStatus.CONFLICT, "NG_009", "타결된 협상이 아닙니다."),
    ROUND_LIMIT_REACHED(HttpStatus.BAD_REQUEST, "NG_010", "라운드 상한(15회)을 소진했습니다."),
    ACCEPT_BREAKS_FLOOR(HttpStatus.BAD_REQUEST, "NG_011",
            "내가 정한 마지노선을 벗어난 제안입니다. 수락하려면 마지노선을 먼저 조정하세요."),
    CHAT_INPUT_DISABLED(HttpStatus.FORBIDDEN, "NG_020", "아직 채팅 입력이 활성화되지 않았습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
