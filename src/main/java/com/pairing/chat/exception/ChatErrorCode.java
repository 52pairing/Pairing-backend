package com.pairing.chat.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 채팅 도메인 에러코드. api-spec 의 CH_* 와 일치한다. */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CH_001", "채팅방을 찾을 수 없습니다."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "CH_002", "채팅방 참여자가 아닙니다."),
    INPUT_DISABLED(HttpStatus.FORBIDDEN, "CH_003", "아직 채팅 입력이 활성화되지 않았습니다."),
    LEAVE_NOT_ALLOWED(HttpStatus.CONFLICT, "CH_004", "아직 채팅방을 나갈 수 없습니다. 대금 지급 완료 후 나갈 수 있습니다."),
    ALREADY_LEFT(HttpStatus.CONFLICT, "CH_005", "이미 나간 채팅방입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
