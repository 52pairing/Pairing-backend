package com.pairing.support.domain.repository;

import com.pairing.support.domain.model.ChatbotMessage;

import java.time.LocalDate;
import java.util.List;

public interface ChatbotMessageRepository {

    ChatbotMessage save(ChatbotMessage message);

    /**
     * 그 날 내가 나눈 대화 전체. 시간순(오래된 것부터).
     *
     * <p>세션 단위로 끊어 보여주지 않는다. 하루 10회 제한이라 하루치를 통째로 보여주는 게 화면과 맞고,
     * 프론트가 sessionId 를 들고 다니지 않아도 새로고침 후 이력이 복원된다.
     */
    List<ChatbotMessage> findByAccountIdAndDate(Long accountId, LocalDate date);
}
