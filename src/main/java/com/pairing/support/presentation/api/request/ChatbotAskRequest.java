package com.pairing.support.presentation.api.request;

import com.pairing.support.application.command.AskChatbotCommand;
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

        /**
         * example 을 두지 않는다. Swagger 가 예시 값을 요청 본문에 그대로 채워 넣는데,
         * 그 세션은 존재하지 않아서 "첫 질문"을 시험하려는 사람이 매번 {@code CB_001} 을 만난다.
         * 비워 두면 Swagger 가 {@code null} 로 채워 실제 첫 질문과 같은 요청이 된다.
         */
        @Schema(description = "이어서 물을 세션 ID. **첫 질문이면 비우거나 null 로 보낸다** "
                + "(서버가 새 세션을 만든다). 이어서 물을 때는 이전 응답의 sessionId 를 그대로 넣는다.")
        Long sessionId,

        @Schema(description = "질문", example = "착수금 수수료는 언제 결제하나요?")
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 500, message = "질문은 500자 이하여야 합니다.")
        String question
) {

    public AskChatbotCommand toCommand(Long accountId) {
        return new AskChatbotCommand(accountId, sessionId, question);
    }
}
