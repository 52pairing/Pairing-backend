package com.pairing.project.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements BaseErrorCode {

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PJ_001", "프로젝트를 찾을 수 없습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "PJ_002", "모집 직군을 찾을 수 없습니다."),
    NOT_PROJECT_OWNER(HttpStatus.FORBIDDEN, "PJ_003", "본인의 프로젝트가 아닙니다."),
    INVALID_PROJECT_FIELD(HttpStatus.BAD_REQUEST, "PJ_004", "프로젝트 정보가 올바르지 않습니다."),
    INVALID_POSITION(HttpStatus.BAD_REQUEST, "PJ_005", "모집 직군 정보가 올바르지 않습니다."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "PJ_006", "현재 상태에서는 처리할 수 없습니다."),
    HEADCOUNT_NOT_CHANGEABLE(HttpStatus.BAD_REQUEST, "PJ_007",
            "착수금 결제 후에는 모집 인원을 변경할 수 없습니다."),
    POSITION_NOT_CHANGEABLE(HttpStatus.BAD_REQUEST, "PJ_008",
            "착수금 결제 후에는 모집 직군을 추가하거나 삭제할 수 없습니다."),
    BUDGET_NOT_CHANGEABLE(HttpStatus.BAD_REQUEST, "PJ_011",
            "착수금 결제 후에는 예산을 변경할 수 없습니다."),
    HEADCOUNT_BELOW_CONFIRMED(HttpStatus.BAD_REQUEST, "PJ_009",
            "이미 확정된 인원보다 적게 줄일 수 없습니다."),
    EXTENSION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PJ_010",
            "모집 기간은 최대 2회까지 연장할 수 있습니다."),
    PROJECT_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "PJ_012",
            "취소되었거나 종료된 프로젝트입니다."),
    POSITION_ALREADY_FILLED(HttpStatus.BAD_REQUEST, "PJ_013",
            "이미 모집이 마감된 직군입니다."),
    RECRUIT_CLOSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PJ_014",
            "현재는 모집을 종료할 수 없습니다."),
    REGISTRATION_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PJ_015",
            "착수금 결제 전에만 등록을 취소할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}