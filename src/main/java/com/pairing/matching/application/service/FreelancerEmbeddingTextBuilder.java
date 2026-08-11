package com.pairing.matching.application.service;

import com.pairing.matching.application.result.FreelancerResumeSummary;

/**
 * 프리랜서 임베딩용 텍스트 조립. 대조 대상은 자기소개+경력사항(.ai/STATE.md "확정된 설계 결정 1")이고,
 * {@link PositionEmbeddingTextBuilder}와 대칭이다.
 */
final class FreelancerEmbeddingTextBuilder {

    /**
     * AI 서버가 받는 상한({@code text: max_length=20000}). 넘겨서 보내면 422로 거절당하는데,
     * 임베딩 호출은 이벤트 리스너에서 예외를 잡아 로그만 남기므로 <b>아무도 모르게 실패</b>한다.
     * 그 프리랜서는 임베딩이 없어 매칭 후보에 영원히 안 잡힌다.
     *
     * <p>포지션 쪽은 원본이 전부 {@code VARCHAR(1500)}이라 상한을 넘길 수 없지만, 여기 원본인
     * {@code resume.self_introduction}과 {@code resume_career.job_description}은 {@code TEXT}라
     * 길이 제한이 없다. 경력이 많으면 실제로 넘길 수 있어서 여기서 자른다.
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
