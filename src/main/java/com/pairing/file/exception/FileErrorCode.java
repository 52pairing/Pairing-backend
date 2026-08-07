package com.pairing.file.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements BaseErrorCode {

    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FI_001", "파일을 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FI_002", "본인이 업로드한 파일만 처리할 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FI_003", "허용된 파일 용량을 초과했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
