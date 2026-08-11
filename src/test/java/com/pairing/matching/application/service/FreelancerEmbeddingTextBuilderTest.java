package com.pairing.matching.application.service;

import com.pairing.matching.application.result.FreelancerResumeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 임베딩 텍스트 길이 상한. AI 서버가 20000자를 넘으면 422로 거절하는데, 임베딩 호출은 이벤트
 * 리스너가 예외를 잡아 로그만 남기므로 <b>아무도 모르게 실패</b>하고 그 프리랜서는 매칭 후보에
 * 영영 안 잡힌다. 원본(resume.self_introduction, resume_career.job_description)이 TEXT라 실제로
 * 넘길 수 있다.
 */
class FreelancerEmbeddingTextBuilderTest {

    @Test
    @DisplayName("경력이 많아 20000자를 넘으면 잘라서 AI 서버 상한을 지킨다")
    void truncatesTextOverServerLimit() {
        String longDescription = "가".repeat(3_000);
        List<FreelancerResumeSummary.CareerEntry> careers = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new FreelancerResumeSummary.CareerEntry("A사" + i, "백엔드팀", longDescription))
                .toList();

        String text = FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary("백엔드 개발자입니다.", careers));

        assertThat(text).hasSize(20_000);
        // 자기소개가 앞이라 잘려도 본인 소개는 남아야 한다.
        assertThat(text).startsWith("백엔드 개발자입니다.");
    }

    @Test
    @DisplayName("상한 이하면 그대로 둔다")
    void keepsShortTextAsIs() {
        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "백엔드 6년차입니다.",
                List.of(new FreelancerResumeSummary.CareerEntry("A사", "백엔드팀 대리", "주문 시스템 개발"))));

        assertThat(text).contains("백엔드 6년차입니다.", "A사 백엔드팀 대리: 주문 시스템 개발");
        assertThat(text.length()).isLessThan(20_000);
    }

    @Test
    @DisplayName("자기소개도 경력도 없으면 빈 문자열이다(호출부가 걸러낸다)")
    void returnsBlankWhenNothingToEmbed() {
        assertThat(FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary(null, List.of()))).isBlank();
    }
}
