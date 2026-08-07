package com.pairing.chat.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 헤더 메시지 배지에 쓰는 값. 모든 채팅방의 미읽음 합계다. */
@Schema(description = "안 읽은 메시지 수 응답")
public record ChatUnreadCountResponse(

        @Schema(description = "안 읽은 메시지 수", example = "3") int unreadCount
) {
}
