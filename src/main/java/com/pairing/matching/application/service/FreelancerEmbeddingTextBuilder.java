package com.pairing.matching.application.service;

import com.pairing.matching.application.result.FreelancerResumeSummary;

/**
 * 프리랜서 임베딩용 텍스트 조립.
 *
 * <p><b>문장만 넣는다.</b> 스킬·경력연차·근무조건·단가·기간 같은 구조화 값은 여기 안 들어간다 —
 * 임베딩은 글이 비슷한지만 볼 뿐 숫자의 크기를 비교하지 못하고, 연차는 방향이 아예 반대로
 * 작동한다(포지션이 "경력 3년 이상"이면 숫자가 같은 "경력 3년"이 "경력 10년"보다 가깝게 나와서
 * 더 자격 있는 사람이 뒤로 밀린다). 그 값들은 DB 조건점수(Pairing-python)가 처리한다.
 * 근거는 `.ai/STATE.md` "2026-08-11 갱신 — 매칭 파이프라인 재설계(팀 확정)" 참고.
 *
 * <p>{@link PositionEmbeddingTextBuilder}와 짝을 맞춰야 한다. 한쪽에만 어떤 항목이 들어가면
 * 대조할 말이 반대쪽에 없어서 그 항목이 유사도에 사실상 반영되지 않는다.
 */
final class FreelancerEmbeddingTextBuilder {

    /**
     * AI 서버가 받는 상한({@code text: max_length=20000}). 넘겨서 보내면 422로 거절당하는데,
     * 임베딩 호출은 이벤트 리스너에서 예외를 잡아 로그만 남기므로 <b>아무도 모르게 실패</b>한다.
     * 그 프리랜서는 임베딩이 없어 매칭 후보에 영원히 안 잡힌다.
     *
     * <p>포지션 쪽은 원본이 전부 {@code VARCHAR(1500)}이라 상한을 넘길 수 없다. 프리랜서 쪽은
     * 필드별로는 제한이 있지만(자기소개 1500자, 경력 담당업무 2000자) <b>경력 건수에 상한이
     * 없다</b>({@code ResumeRequest}는 {@code @NotEmpty}만 건다). 경력 10건이면 2만 자를
     * 그대로 넘기므로 여기서 자른다.
     */
    private static final int MAX_TEXT_LENGTH = 20_000;

    private FreelancerEmbeddingTextBuilder() {
    }

    static String buildText(FreelancerResumeSummary summary) {
        StringBuilder text = new StringBuilder(
                summary.selfIntroduction() != null ? summary.selfIntroduction() : "");

        for (FreelancerResumeSummary.CareerEntry career : summary.careers()) {
            text.append('\n').append(career.companyName());
            if (career.departmentRank() != null && !career.departmentRank().isBlank()) {
                text.append(' ').append(career.departmentRank());
            }
            if (career.jobDescription() != null && !career.jobDescription().isBlank()) {
                text.append(": ").append(career.jobDescription());
            }
        }

        // 자기소개가 앞이라 잘려도 핵심(본인 소개)은 남는다. 뒤쪽 경력이 일부 빠지는 편이
        // 임베딩 자체가 안 만들어지는 것보다 낫다.
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text.toString();
    }
}
