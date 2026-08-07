package com.pairing.notification.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 헤더 알림 배지에 쓰는 값. */
@Schema(description = "읽지 않은 알림 수 응답")
public record UnreadCountResponse(

        @Schema(description = "읽지 않은 알림 수", example = "3") int unreadCount
) {
}
