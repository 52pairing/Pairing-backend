package com.pairing.contract.domain.model;

/**
 * 계약서에 찍히는 자유 텍스트 셋. (계약서 제2조·제15조)
 *
 * <p>금액·기간·날짜·당사자는 여기 담지 않는다. 그 값들은 DB 에서 그대로 가져와 채운다.
 * AI 가 손대는 것은 <b>이미 확정된 원문을 계약서 문체로 줄이는 일</b>뿐이다.
 * 법적 문서라 LLM 이 숫자를 하나라도 바꾸면 그대로 서명되고 분쟁 근거가 된다.
 *
 * <p>AI 호출이 실패해도 계약은 체결되어야 하므로 {@link #defaults} 로 원문을 잘라 쓴다.
 */
public record ContractDraftText(String mainTaskSummary, String detailScopeSummary, String specialTerms) {

    /** 계약서 표 칸이 깨지지 않는 길이. 파이썬 쪽 가드와 같은 값을 쓴다. */
    private static final int MAIN_TASK_LIMIT = 80;
    private static final int DETAIL_SCOPE_LIMIT = 200;
    private static final String ELLIPSIS = "…";

    public static final String NO_SPECIAL_TERMS = "별도의 특약사항 없음";

    /** AI 실패 시 대체값. 모양은 덜 다듬어져도 계약은 체결되어야 한다. */
    public static ContractDraftText defaults(String mainTask, String detailScope) {
        return new ContractDraftText(
                clip(mainTask, MAIN_TASK_LIMIT),
                clip(detailScope, DETAIL_SCOPE_LIMIT),
                NO_SPECIAL_TERMS);
    }

    private static String clip(String source, int limit) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String trimmed = source.strip();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit) + ELLIPSIS;
    }
}
