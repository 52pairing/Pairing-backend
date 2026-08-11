package com.pairing.support.application.port.out;

/** AI 서버(Pairing-python) 챗봇 호출 포트. */
public interface ChatbotAiPort {

    /** 질문을 보내고 답변과 이어질 화면 코드를 받는다. 실패 시 {@code CB_004}. */
    Answer ask(String question);

    /**
     * @param answer 답변 본문
     * @param intent 이어질 화면 코드. 목록에 없거나 없으면 {@code NONE}
     */
    record Answer(String answer, String intent) {
    }
}
