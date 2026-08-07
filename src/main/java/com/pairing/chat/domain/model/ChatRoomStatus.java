package com.pairing.chat.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 채팅방 상태. */
@Getter
@RequiredArgsConstructor
public enum ChatRoomStatus {

    ACTIVE("진행중"),
    CLOSED("종료");

    private final String label;
}
