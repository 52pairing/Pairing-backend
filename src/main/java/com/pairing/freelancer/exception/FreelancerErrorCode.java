package com.pairing.freelancer.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FreelancerErrorCode implements BaseErrorCode {

    CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "FR_001", "등록된 조건이 없습니다."),
    INVALID_CONDITION_FIELD(HttpStatus.BAD_REQUEST, "FR_002", "조건 정보가 올바르지 않습니다."),
    INVALID_RESUME_FIELD(HttpStatus.BAD_REQUEST, "FR_003", "이력서 정보가 올바르지 않습니다."),
    AGREEMENTS_REQUIRED(HttpStatus.BAD_REQUEST, "FR_004", "필수 약관에 모두 동의해야 합니다."),
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "FR_005", "존재하지 않는 프리랜서입니다."),
    DRAFT_TOO_LARGE(HttpStatus.BAD_REQUEST, "FR_006", "임시 저장할 내용이 너무 큽니다."),
    /** 같은 스킬을 두 번 담아 보낸 경우. 숙련도가 달라도 중복으로 본다. */
    DUPLICATE_SKILL(HttpStatus.BAD_REQUEST, "FR_007", "같은 스킬을 중복해서 선택할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
