package com.pairing.notification.presentation.api.request;

import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.domain.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "내부 서버가 보내는 알림 생성 요청")
public record InternalNotificationCreateRequest(

        @Schema(description = "알림을 받을 계정 ID", example = "18")
        @NotNull(message = "수신자 계정 ID는 필수입니다.")
        Long ownerAccountId,

        @Schema(description = "알림 종류", example = "INQUIRY_ANSWERED")
        @NotNull(message = "알림 종류는 필수입니다.")
        NotificationType type,

        @Schema(description = "제목", example = "문의하신 내용에 답변이 등록되었습니다.")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Schema(description = "본문. 없으면 목록에 제목만 보인다.")
        @Size(max = 500, message = "본문은 500자를 넘을 수 없습니다.")
        String content,

        @Schema(description = "알림을 눌렀을 때 이동할 프론트 경로", example = "/support/inquiries/42")
        @Size(max = 300, message = "이동 경로는 300자를 넘을 수 없습니다.")
        String linkUrl
) {

    public CreateNotificationCommand toCommand() {
        return new CreateNotificationCommand(ownerAccountId, type, title, content, linkUrl);
    }
}
