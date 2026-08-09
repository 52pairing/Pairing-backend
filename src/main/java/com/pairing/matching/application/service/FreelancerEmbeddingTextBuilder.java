package com.pairing.matching.application.service;

import com.pairing.matching.application.result.FreelancerResumeSummary;

/**
 * 프리랜서 임베딩용 텍스트 조립. 대조 대상은 자기소개+경력사항(.ai/STATE.md "확정된 설계 결정 1")이고,
 * {@link PositionEmbeddingTextBuilder}와 대칭이다.
 */
final class FreelancerEmbeddingTextBuilder {

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
        return text.toString();
    }
}
