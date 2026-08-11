package com.pairing.support.application.usecase;

import com.pairing.support.application.command.AskChatbotCommand;
import com.pairing.support.application.result.ChatbotAnswerResult;
import com.pairing.support.application.result.ChatbotQuotaResult;

import java.util.List;

public interface ChatbotUseCase {

    /** 하루 한도를 넘기면 {@code CB_003}(429), AI 서버 호출에 실패하면 {@code CB_004}(502). */
    ChatbotAnswerResult ask(AskChatbotCommand command);

    ChatbotQuotaResult getQuota(Long accountId);

    /**
     * 오늘 내가 나눈 대화 전체. 시간순이고, 없으면 빈 목록이다.
     *
     * <p>세션 단위로 끊지 않는다. 화면은 하루치를 한 흐름으로 보여주고, 이어서 물을 때 쓸 sessionId 는
     * 마지막 항목에서 꺼내 쓰면 된다.
     */
    List<ChatbotAnswerResult> findTodayMessages(Long accountId);
}
