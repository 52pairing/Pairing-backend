package com.pairing.support.application.port.out;

/** AI 서버(Pairing-python) 챗봇 호출 포트. */
public interface ChatbotAiPort {

    /** 질문을 보내고 답변 텍스트만 받는다. 실패 시 {@code CB_004}. */
    String ask(String question);
}
