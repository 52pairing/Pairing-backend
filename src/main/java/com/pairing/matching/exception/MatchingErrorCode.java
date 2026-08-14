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
    /**
     * <b>지금은 아무도 던지지 않는다(2026-08-14).</b> 재추천에 걸려 있던 레이트리밋(계정당 5초 1회 /
     * 1시간 10회)을 없앴다 — 한도가 프로젝트를 구분하지 않아, 여러 프로젝트를 동시에 진행하는
     * 클라이언트가 정상 사용 중에 막혔다. 총량은 비즈니스 규칙(무료 1회 / 유료 5회, 프로젝트 단위)이
     * 이미 제한한다. 열거값은 남겨둔다 — 프론트가 이 코드를 이미 다루고 있고, 다시 필요해질 수 있다.
     */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "MT_011", "잠시 후 다시 시도해 주세요."),
    QUANTITY_REQUIRED(HttpStatus.BAD_REQUEST, "MT_012", "유료 재추천은 인원 수(quantity)를 입력해야 합니다."),
    INVALID_RERECOMMEND_TYPE(HttpStatus.BAD_REQUEST, "MT_013", "재추천 종류는 FREE 또는 PAID만 가능합니다."),
    PROJECT_RECRUITING_CLOSED(HttpStatus.BAD_REQUEST, "MT_014", "모집이 종료되었거나 취소된 프로젝트입니다."),
    FREELANCER_NOT_FOUND(HttpStatus.NOT_FOUND, "MT_015", "프리랜서를 찾을 수 없습니다."),
    REQUEST_EXPIRED(HttpStatus.BAD_REQUEST, "MT_016", "응답 기한이 지나 요청이 자동 만료되었습니다."),
    CANDIDATE_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "MT_017", "선택할 수 없는 후보입니다."),
    POSITION_ALREADY_FILLED(HttpStatus.BAD_REQUEST, "MT_018", "모집 인원이 모두 채워져 재추천할 수 없습니다."),
    CANDIDATE_ALREADY_REQUESTED(HttpStatus.BAD_REQUEST, "MT_019", "이미 매칭 요청을 보낸 후보는 거절할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
