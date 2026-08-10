package com.pairing.support.application.usecase;

import com.pairing.support.application.command.AskChatbotCommand;
import com.pairing.support.application.result.ChatbotAnswerResult;
import com.pairing.support.application.result.ChatbotQuotaResult;

import java.util.List;

public interface ChatbotUseCase {

    /** 하루 한도를 넘기면 {@code CB_003}(429), AI 서버 호출에 실패하면 {@code CB_004}(502). */
    ChatbotAnswerResult ask(AskChatbotCommand command);

    ChatbotQuotaResult getQuota(Long accountId);

    /** 본인 세션이 아니면 {@code CB_002}, 없으면 {@code CB_001}. */
    List<ChatbotAnswerResult> findMessages(Long accountId, Long sessionId);
}
