package com.pairing.chat.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 채팅 메시지 전송. (요구사항 R23)
 *
 * <p>협상이 끝나야 입력창이 열린다. 그 전에는 A2A 협상 응답만 가능하다.
 */
@Schema(description = "채팅 메시지 전송 요청")
public record ChatMessageSendRequest(

        @Schema(description = "메시지 본문. 한 번에 500자까지", example = "안녕하세요, 일정 조율 가능할까요?")
        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 500, message = "메시지는 500자 이하여야 합니다.")
        String content
) {
}
