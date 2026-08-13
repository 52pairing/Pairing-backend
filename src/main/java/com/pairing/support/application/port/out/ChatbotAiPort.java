package com.pairing.support.application.port.out;

/** AI 서버(Pairing-python) 챗봇 호출 포트. */
public interface ChatbotAiPort {

    /** 질문을 보내고 답변과 이어질 화면 코드를 받는다. 실패 시 {@code CB_004}. */
    Answer ask(String question);

    /**
     * @param answer     답변 본문
     * @param intent     이어질 화면 코드. 목록에 없거나 없으면 {@code NONE}
     * @param outOfScope 페어링과 무관한 질문이라 답하지 않았다는 표시.
     *                   AI 서버가 임베딩 유사도로 걸렀거나 LLM 이 스스로 판단한 경우다.
     *                   <b>이때는 하루 사용량을 차감하지 않는다</b> — 답을 못 받았는데 횟수만 빠지면
     *                   오타 한 번에 1회가 날아간다
     */
    record Answer(String answer, String intent, boolean outOfScope) {
    }
}
