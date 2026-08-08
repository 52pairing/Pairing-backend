package com.pairing.notification.presentation.api.response;

import com.pairing.notification.application.result.NotificationResult;
import com.pairing.notification.domain.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 알림 1건. */
@Schema(description = "알림 응답")
public record NotificationResponse(

        @Schema(description = "알림 ID", example = "1100") Long notificationId,
        @Schema(description = "알림 종류") NotificationType type,
        @Schema(description = "제목", example = "매칭 요청이 도착했습니다") String title,
        @Schema(description = "본문", example = "페어링 웹 리뉴얼 프로젝트에서 매칭 요청을 보냈습니다.") String content,
        @Schema(description = "이동 경로. 알림을 누르면 이 경로로 보낸다.",
                example = "/matchings/requests/200") String linkUrl,
        @Schema(description = "읽음 여부", example = "false") boolean read,
        @Schema(description = "발생 시각") LocalDateTime createdAt
) {

    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(result.notificationId(), result.type(), result.title(), result.content(),
                result.linkUrl(), result.read(), result.createdAt());
    }
}
