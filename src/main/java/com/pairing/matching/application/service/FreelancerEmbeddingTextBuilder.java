package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.result.FreelancerResumeSummary;

import java.util.stream.Collectors;

/**
 * 프리랜서 임베딩용 텍스트 조립. 요구사항 R01.2 "프로젝트 요구조건 ↔ 프리랜서 <b>프로필·이력서</b>
 * 벡터 비교"에 따라 이력서(자기소개+경력사항)와 조건(프로필)을 <b>둘 다</b> 넣는다.
 *
 * <p><b>2026-08-11 수정 — 포지션 쪽과 짝이 안 맞던 것을 맞췄다.</b> 그 전에는 자기소개+경력사항만
 * 넣었는데, {@link PositionEmbeddingTextBuilder}는 이미 요구스킬·최소경력·근무조건·기간을 넣고
 * 있었다. 그래서 포지션 벡터에는 "Java, Spring Boot / 경력 3년 이상 / 상주 · 풀타임"이 들어가는데
 * 프리랜서 벡터에는 대응하는 말이 아예 없어서, <b>프리랜서가 자기소개에 우연히 "자바"라고 써둔
 * 경우에만</b> 그 단어가 걸렸다. 스킬·연차·근무조건이 1차 추림에 사실상 반영되지 않던 상태다.
 *
 * <p><b>단가(payAmount)와 시작 가능일(availableFrom)은 일부러 안 넣는다.</b> 임베딩은 숫자를
 * 비교하지 못해서 "월 500만원"과 "월 5000만원"이 벡터상 거의 같게 취급된다 — 넣으면 정확도가
 * 오히려 떨어진다. 포지션 쪽에도 예산·시작희망일이 없어서 넣으면 짝이 다시 어긋난다. 이 둘은
 * Stage E(LLM 최종선정)가 원본 숫자를 보고 감점으로 처리한다(`.ai/STATE.md` "Stage B 조건필터 폐기").
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

    /**
     * @param condition 아직 조건을 등록하지 않은 프리랜서면 null. 이력서만으로도 임베딩은 만든다 —
     *                  조건이 없다고 벡터 자체를 포기하면 그 사람은 후보에 아예 안 잡힌다.
     */
    static String buildText(FreelancerResumeSummary summary, FreelancerConditionResponse condition) {
        StringBuilder text = new StringBuilder(
                summary.selfIntroduction() != null ? summary.selfIntroduction() : "");

        // 조건을 경력보다 앞에 붙인다. 뒤쪽은 길이 상한에 걸려 잘려나갈 수 있는데, 스킬·연차 같은
        // 짧고 핵심적인 값이 긴 경력 설명에 밀려 사라지면 안 된다.
        appendCondition(text, condition);

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

    /** {@link PositionEmbeddingTextBuilder}의 직무/요구스킬/최소경력/근무조건/기간과 1:1로 대응한다. */
    private static void appendCondition(StringBuilder text, FreelancerConditionResponse condition) {
        if (condition == null) {
            return;
        }
        if (condition.jobRole() != null) {
            text.append('\n').append(condition.jobRole().getLabel());
        }
        if (!condition.skills().isEmpty()) {
            text.append('\n').append(condition.skills().stream()
                    .map(skill -> skill.skillCode().getLabel())
                    .collect(Collectors.joining(", ")));
        }
        text.append('\n').append("경력 ").append(condition.careerYears()).append("년");
        if (condition.workStyle() != null && condition.workForm() != null) {
            text.append('\n').append(condition.workStyle().getLabel())
                    .append(" · ").append(condition.workForm().getLabel());
        }
        // 기간은 "협의 가능"이면 값이 없다(FreelancerConditionResponse.periodValue Javadoc).
        if (condition.periodValue() != null && condition.periodUnit() != null) {
            text.append('\n').append(condition.periodValue()).append(condition.periodUnit().getLabel());
        }
    }
}
