package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프리랜서 임베딩 텍스트 조립. 두 가지를 지킨다.
 *
 * <p>하나, <b>포지션 쪽과 짝 맞추기</b>. {@code PositionEmbeddingTextBuilder}가 요구스킬·최소경력·
 * 근무조건·기간을 넣는데 여기가 안 넣으면, 포지션 벡터의 "Java / 경력 3년 이상 / 상주 · 풀타임"에
 * 대응할 말이 프리랜서 벡터에 없어서 1차 추림이 그 항목들을 사실상 못 본다.
 *
 * <p>둘, <b>길이 상한</b>. AI 서버가 20000자를 넘으면 422로 거절하는데 호출부가 예외를 잡아 로그만
 * 남기므로 아무도 모르게 실패하고 그 프리랜서는 후보에 영영 안 잡힌다. 원본
 * (resume.self_introduction, resume_career.job_description)이 TEXT라 실제로 넘길 수 있다.
 */
class FreelancerEmbeddingTextBuilderTest {

    @Test
    @DisplayName("조건의 직무·스킬·경력·근무조건·기간이 텍스트에 들어간다")
    void includesConditionFields() {
        String text = FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary("백엔드 6년차입니다.",
                        List.of(new FreelancerResumeSummary.CareerEntry("A사", "백엔드팀 대리", "주문 시스템 개발"))),
                condition());

        assertThat(text).contains("백엔드", "Java, Spring Boot", "경력 5년", "재택 · 풀타임", "6개월");
        assertThat(text).contains("백엔드 6년차입니다.", "A사 백엔드팀 대리: 주문 시스템 개발");
    }

    @Test
    @DisplayName("단가·시작 가능일은 넣지 않는다(임베딩은 숫자를 비교하지 못한다)")
    void excludesNumericConditionFields() {
        String text = FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary("백엔드 6년차입니다.", List.of()), condition());

        assertThat(text).doesNotContain("6500000", "5500000", "2026-09-01");
    }

    @Test
    @DisplayName("조건을 아직 등록 안 했으면 이력서만으로 조립한다(벡터 자체를 포기하지 않는다)")
    void buildsFromResumeOnlyWhenConditionMissing() {
        String text = FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary("백엔드 6년차입니다.",
                        List.of(new FreelancerResumeSummary.CareerEntry("A사", "백엔드팀 대리", "주문 시스템 개발"))),
                null);

        assertThat(text).contains("백엔드 6년차입니다.", "A사 백엔드팀 대리: 주문 시스템 개발");
    }

    @Test
    @DisplayName("경력이 많아 20000자를 넘으면 잘라서 AI 서버 상한을 지킨다")
    void truncatesTextOverServerLimit() {
        String longDescription = "가".repeat(3_000);
        List<FreelancerResumeSummary.CareerEntry> careers = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new FreelancerResumeSummary.CareerEntry("A사" + i, "백엔드팀", longDescription))
                .toList();

        String text = FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary("백엔드 개발자입니다.", careers), condition());

        assertThat(text).hasSize(20_000);
        // 자기소개가 앞이라 잘려도 본인 소개는 남아야 한다.
        assertThat(text).startsWith("백엔드 개발자입니다.");
        // 조건은 경력보다 앞에 붙으므로 긴 경력에 밀려 잘려나가면 안 된다.
        assertThat(text).contains("Java, Spring Boot", "경력 5년");
    }

    @Test
    @DisplayName("상한 이하면 그대로 둔다")
    void keepsShortTextAsIs() {
        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "백엔드 6년차입니다.",
                List.of(new FreelancerResumeSummary.CareerEntry("A사", "백엔드팀 대리", "주문 시스템 개발"))), null);

        assertThat(text).contains("백엔드 6년차입니다.", "A사 백엔드팀 대리: 주문 시스템 개발");
        assertThat(text.length()).isLessThan(20_000);
    }

    @Test
    @DisplayName("자기소개도 경력도 조건도 없으면 빈 문자열이다(호출부가 걸러낸다)")
    void returnsBlankWhenNothingToEmbed() {
        assertThat(FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary(null, List.of()), null)).isBlank();
    }

    private static FreelancerConditionResponse condition() {
        return new FreelancerConditionResponse(
                50L, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.of(2026, 9, 1), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new FreelancerConditionResponse.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new FreelancerConditionResponse.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED)));
    }
}
