package com.pairing.chat.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 채팅 메시지 종류. 시스템 안내와 사용자 발화를 구분한다. */
@Getter
@RequiredArgsConstructor
public enum ChatMessageType {

    TEXT("일반 메시지"),
    SYSTEM("시스템 안내");

    private final String label;
}
