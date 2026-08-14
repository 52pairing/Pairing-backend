package com.pairing.support.application.usecase;

import com.pairing.support.application.command.AskChatbotCommand;
import com.pairing.support.application.result.ChatbotAnswerResult;
import com.pairing.support.application.result.ChatbotQuotaResult;
import com.pairing.support.application.port.out.ChatbotAiPort;

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

    /**
     * [운영] AI 서버의 관련성 판정용 지식을 다시 임베딩한다.
     *
     * <p>정책 문구를 고쳤을 때 부른다. 안 부르면 챗봇은 새 문구로 답하는데 관련성 판정은 옛 문구로
     * 해서, <b>답할 수 있는 질문이 관문에서 막히는</b> 상태가 된다.
     *
     * <p>사용량과 무관하다. 사람이 누르는 운영 작업이라 하루 10회 한도를 깎지 않는다.
     */
    ChatbotAiPort.KnowledgeReindexResult reindexKnowledge();
}
