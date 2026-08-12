package com.pairing.matching.application.service;

import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 포지션 임베딩 텍스트 조립 규칙(2026-08-11 재설계).
 *
 * <p>여기서 지키는 건 <b>프리랜서 쪽과 짝이 맞는지</b>다. 포지션에만 있고 프리랜서 벡터엔
 * 대응하는 말이 없는 항목을 넣으면 유사도가 우연에 좌우된다 — 이번 재설계의 발단이 된 버그가
 * 정확히 그것이라 회귀로 남긴다.
 */
class PositionEmbeddingTextBuilderTest {

    @Test
    @DisplayName("프로젝트명·진행상황·담당업무·업무범위·우대사항만 넣는다")
    void includesOnlyFreeFormFields() {
        String text = PositionEmbeddingTextBuilder.buildText(summary(
                "AI 추천 시스템 구축", "기존 서비스 리뉴얼", "추천 API 설계", "모델 서빙까지", "컴퓨터공학과 우대"));

        assertThat(text).contains("AI 추천 시스템 구축", "기존 서비스 리뉴얼", "추천 API 설계",
                "모델 서빙까지", "컴퓨터공학과 우대");
    }

    @Test
    @DisplayName("직무·요구스킬·최소경력·근무조건·기간은 넣지 않는다")
    void excludesStructuredFields() {
        String text = PositionEmbeddingTextBuilder.buildText(summary(
                "AI 추천 시스템 구축", "기존 서비스 리뉴얼", "추천 API 설계", "모델 서빙까지", "컴퓨터공학과 우대"));

        // 스킬을 넣으면 프리랜서가 자기소개에 우연히 "자바"라고 써둔 경우에만 걸린다.
        assertThat(text).doesNotContain("JAVA", "SPRING_BOOT", JobRole.BACKEND.getLabel());
        // 연차는 방향이 반대로 작동한다("3년 이상"에 "3년"이 "10년"보다 가깝게 나온다).
        assertThat(text).doesNotContain("경력", "3년");
        assertThat(text).doesNotContain("재택/풀타임", "4개월");
    }

    @Test
    @DisplayName("우대사항은 반드시 들어간다 — 프리랜서 학과와 세트다")
    void keepsExtraNoteWhichPairsWithMajors() {
        String text = PositionEmbeddingTextBuilder.buildText(summary(
                "사내 ERP 고도화", null, null, null, "컴퓨터공학과 우대"));

        // 프리랜서 쪽에 학과를 넣은 이유가 이걸 잡기 위해서라, 한쪽을 빼면 둘 다 의미가 없어진다.
        assertThat(text).contains("컴퓨터공학과 우대");
    }

    @Test
    @DisplayName("값 없는 항목은 줄째로 뺀다")
    void skipsBlankValues() {
        String text = PositionEmbeddingTextBuilder.buildText(summary(
                "사내 ERP 고도화", null, "  ", "모델 서빙까지", null));

        // 빈 줄이 남으면 그 자리에 의미 없는 토큰이 들어가, 항목 수가 적은 프로젝트끼리
        // 서로 비슷해 보이는 쪽으로 벡터가 밀린다.
        assertThat(text).isEqualTo("사내 ERP 고도화\n모델 서빙까지");
    }

    private static ProjectPositionSummary summary(String title, String currentSituation, String mainTask,
                                                  String detailScope, String extraNote) {
        return new ProjectPositionSummary(
                1L, title, "회사명", "IT/50명", JobRole.BACKEND,
                List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT), 3, "재택/풀타임", "4개월",
                4, PeriodUnit.MONTH, null, 50_000_000L, 1, 1,
                currentSituation, mainTask, detailScope, extraNote);
    }
}
