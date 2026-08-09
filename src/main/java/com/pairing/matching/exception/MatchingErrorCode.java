package com.pairing.matching.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode implements BaseErrorCode {

    ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_001", "추천 라운드를 찾을 수 없습니다."),
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_002", "후보를 찾을 수 없습니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_003", "매칭 요청을 찾을 수 없습니다."),
    SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_004", "매칭 스냅샷을 찾을 수 없습니다."),
    HEADCOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "MT_005", "모집 인원을 초과해 선택할 수 없습니다."),
    ALREADY_RESPONDED(HttpStatus.BAD_REQUEST, "MT_006", "이미 응답한 매칭 요청입니다."),
    INVALID_MATCHING_STATE(HttpStatus.BAD_REQUEST, "MT_007", "현재 상태에서는 처리할 수 없습니다."),
    RERECOMMEND_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "MT_008", "재추천을 사용할 수 없습니다."),
    CANDIDATE_POOL_EMPTY(HttpStatus.NOT_FOUND, "MT_009", "추천할 후보가 없습니다."),
    AI_SERVER_CALL_FAILED(HttpStatus.BAD_GATEWAY, "MT_010", "AI 매칭 서버 호출에 실패했습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "MT_011", "잠시 후 다시 시도해 주세요."),
    QUANTITY_REQUIRED(HttpStatus.BAD_REQUEST, "MT_012", "유료 재추천은 인원 수(quantity)를 입력해야 합니다."),
    INVALID_RERECOMMEND_TYPE(HttpStatus.BAD_REQUEST, "MT_013", "재추천 종류는 FREE 또는 PAID만 가능합니다."),
    PROJECT_RECRUITING_CLOSED(HttpStatus.BAD_REQUEST, "MT_014", "모집이 종료되었거나 취소된 프로젝트입니다."),
    FREELANCER_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_015", "프리랜서를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
