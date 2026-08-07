package com.pairing.support.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 챗봇 질의. (요구사항 R44)
 *
 * <p>FAQ·이용안내·정책 범위 안에서만 답한다. A2A 협상 채팅과는 별개다.
 * 하루 10회로 제한된다.
 */
@Schema(description = "챗봇 질의 요청")
public record ChatbotAskRequest(

        @Schema(description = "이어서 물을 세션 ID. 첫 질문이면 비운다.", example = "1200")
        Long sessionId,

        @Schema(description = "질문", example = "착수금 수수료는 언제 결제하나요?")
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 500, message = "질문은 500자 이하여야 합니다.")
        String question
) {
}
