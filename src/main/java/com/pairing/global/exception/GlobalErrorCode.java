package com.pairing.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_001", "서버 내부에서 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL_002", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "GLOBAL_003", "지원하지 않는 HTTP 메서드입니다."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "GLOBAL_004", "요청하신 API 경로를 찾을 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "GLOBAL_005", "접근 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "GLOBAL_006", "로그인이 필요한 서비스입니다. 인증 토큰을 확인해 주세요."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_007", "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "GLOBAL_008", "허용되지 않는 파일 형식입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "GLOBAL_009", "토큰이 만료되었습니다. 다시 로그인해주세요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "GLOBAL_010", "유효하지 않은 토큰입니다."),
    SESSION_TERMINATED(HttpStatus.UNAUTHORIZED, "GLOBAL_011", "다른 기기에서 로그인되어 로그아웃되었습니다."),
    // 중복 등록·참조 중인 데이터 삭제처럼 사용자가 요청을 바꾸면 풀리는 충돌.
    DATA_CONFLICT(HttpStatus.CONFLICT, "GLOBAL_012", "이미 존재하거나 다른 데이터가 참조 중입니다."),
    // CHECK/NOT NULL 위반. 사용자가 어떻게 해도 안 풀리는 서버·스키마 문제라 500으로 둔다.
    DATA_INTEGRITY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_013", "데이터 제약 조건을 만족하지 못했습니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "GLOBAL_014", "요청 크기가 서버 허용치를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "GLOBAL_015", "지원하지 않는 Content-Type 입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
