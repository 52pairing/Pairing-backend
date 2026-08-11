package com.pairing.matching.application.service;

import com.pairing.matching.application.result.FreelancerResumeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프리랜서 임베딩 텍스트 조립 규칙(자기소개 + 학과 전부 + 경력 담당업무)과 길이 상한.
 *
 * <p>길이 상한: AI 서버가 20000자를 넘으면 422로 거절하는데, 임베딩 호출은 이벤트 리스너가
 * 예외를 잡아 로그만 남기므로 <b>아무도 모르게 실패</b>하고 그 프리랜서는 매칭 후보에 영영
 * 안 잡힌다. 경력 건수에 상한이 없어 실제로 넘길 수 있다.
 */
class FreelancerEmbeddingTextBuilderTest {

    @Test
    @DisplayName("자기소개 + 학과 전부 + 경력 담당업무를 넣는다")
    void includesIntroductionMajorsAndCareerDescriptions() {
        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "백엔드 6년차입니다.",
                List.of("컴퓨터공학과", "경영학과"),
                List.of("주문 시스템 개발", "결제 API 구축")));

        assertThat(text).contains("백엔드 6년차입니다.", "컴퓨터공학과", "경영학과",
                "주문 시스템 개발", "결제 API 구축");
    }

    @Test
    @DisplayName("학력이 여러 개면 학과를 전부 넣는다 — 최종학력만 보면 전공이 맞는 사람을 놓친다")
    void includesEveryMajorNotOnlyTheLatest() {
        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "안녕하세요.", List.of("경영학과", "컴퓨터공학과"), List.of()));

        // 석사 경영학 + 학부 컴공인 사람이 "컴퓨터공학과 우대" 프로젝트에 걸려야 한다.
        assertThat(text).contains("경영학과").contains("컴퓨터공학과");
    }

    @Test
    @DisplayName("학과 미입력·담당업무 공백은 줄째로 뺀다")
    void skipsBlankValues() {
        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "안녕하세요.",
                Arrays.asList(null, "  ", "컴퓨터공학과"),
                Arrays.asList("주문 시스템 개발", null)));

        // 빈 줄이 남으면 그 자리에 의미 없는 토큰이 들어가 벡터가 흔들린다.
        assertThat(text).isEqualTo("안녕하세요.\n컴퓨터공학과\n주문 시스템 개발");
    }

    @Test
    @DisplayName("경력이 많아 20000자를 넘으면 잘라서 AI 서버 상한을 지킨다")
    void truncatesTextOverServerLimit() {
        String longDescription = "가".repeat(3_000);
        List<String> careerDescriptions = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> longDescription)
                .toList();

        String text = FreelancerEmbeddingTextBuilder.buildText(new FreelancerResumeSummary(
                "백엔드 개발자입니다.", List.of("컴퓨터공학과"), careerDescriptions));

        assertThat(text).hasSize(20_000);
        // 자기소개가 앞이라 잘려도 본인 소개는 남아야 한다.
        assertThat(text).startsWith("백엔드 개발자입니다.");
        // 학과는 경력보다 앞이라 경력이 아무리 많아도 살아남는다.
        assertThat(text).contains("컴퓨터공학과");
    }

    @Test
    @DisplayName("자기소개도 학과도 경력도 없으면 빈 문자열이다(호출부가 걸러낸다)")
    void returnsBlankWhenNothingToEmbed() {
        assertThat(FreelancerEmbeddingTextBuilder.buildText(
                new FreelancerResumeSummary(null, List.of(), List.of()))).isBlank();
    }
}
