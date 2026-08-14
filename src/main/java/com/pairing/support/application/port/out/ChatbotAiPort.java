package com.pairing.support.application.port.out;

/** AI 서버(Pairing-python) 챗봇 호출 포트. */
public interface ChatbotAiPort {

    /** 질문을 보내고 답변과 이어질 화면 코드를 받는다. 실패 시 {@code CB_004}. */
    Answer ask(String question);

    /**
     * AI 서버의 관련성 판정용 지식을 다시 임베딩한다. 실패 시 {@code CB_004}.
     *
     * <p>AI 서버는 외부에 열려 있지 않아 사람이 직접 부를 수 없다(ECS 내부). 정책 문구를 고칠
     * 때마다 컨테이너에 들어가야 하는 것을 피하려고 스프링이 통로를 대신 연다.
     *
     * <p>여러 번 불러도 안전하다. 문구가 그대로면 AI 서버가 임베딩 API 를 호출하지 않는다.
     */
    KnowledgeReindexResult reindexKnowledge();

    /**
     * @param total    전체 지식 청크 수
     * @param embedded 이번에 새로 임베딩한 수(신규·문구 변경)
     * @param skipped  문구가 그대로여서 건너뛴 수
     */
    record KnowledgeReindexResult(int total, int embedded, int skipped) {
    }

    /**
     * @param answer      답변 본문
     * @param intent      이어질 화면 코드. 목록에 없거나 없으면 {@code NONE}
     * @param chargeQuota 하루 사용량을 깎을지. AI 서버가 판단해서 내려준다.
     *                    <p>범위 밖 질문(답을 못 줌)과 단순 인사(질문이 아님)는 false 다.
     *                    답을 못 받았는데 횟수만 빠지면 오타 한 번에 1회가 날아가고,
     *                    "안녕" 몇 번에 하루치가 준다.
     *                    <p>판단을 여기서 다시 하지 않는다 — 무엇을 답으로 쳐줄지는 답을 만든
     *                    쪽이 안다. 스프링이 문구를 보고 되짚으면 규칙이 두 곳에 흩어진다.
     */
    record Answer(String answer, String intent, boolean chargeQuota) {
    }
}
