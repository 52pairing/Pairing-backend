package com.pairing.chat.presentation.api.response;

import com.pairing.chat.domain.model.ChatMessageType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 채팅 메시지. */
@Schema(description = "채팅 메시지 응답")
public record ChatMessageResponse(

        @Schema(description = "메시지 ID", example = "1000") Long messageId,
        @Schema(description = "보낸 사람 계정 ID. 시스템 메시지는 null", example = "3") Long senderId,
        @Schema(description = "보낸 사람 이름", example = "홍길동") String senderName,
        @Schema(description = "내가 보낸 메시지인지", example = "false") boolean mine,
        @Schema(description = "메시지 종류") ChatMessageType messageType,
        @Schema(description = "본문", example = "안녕하세요, 일정 조율 가능할까요?") String content,
        @Schema(description = "전송 시각") LocalDateTime createdAt
) {
}
