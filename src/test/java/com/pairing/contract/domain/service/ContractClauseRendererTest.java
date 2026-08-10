package com.pairing.contract.domain.service;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractClause;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 계약서 본문 렌더링 단위 검증. Spring 없이 순수 로직만 본다. */
class ContractClauseRendererTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    private Contract contract(long salaryAmount, int months, WorkStyle workStyle,
                              String workLocation, String specialTerms) {
        return Contract.create(300L, 1L, 10L, 100L, 200L, 1000L, 2000L,
                salaryAmount, months, START, START.plusMonths(months).minusDays(1),
                workStyle, WorkForm.FULL_TIME, workLocation, specialTerms);
    }

    private Map<Integer, String> byNo(List<ContractClause> clauses) {
        return clauses.stream().collect(Collectors.toMap(ContractClause::no, ContractClause::content));
    }

    @Test
    @DisplayName("제4조 금액과 제3조 기간의 개월 수가 서로 맞는다")
    void amountAndPeriodAgree() {
        List<ContractClause> clauses = ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), "페어링 웹 리뉴얼", JobRole.BACKEND);

        Map<Integer, String> content = byNo(clauses);

        assertThat(content.get(3))
                .contains("2026년 9월 1일")
                .contains("2026년 12월 31일")
                .contains("4개월");
        assertThat(content.get(4))
                .contains("5,000,000원")
                .contains("20,000,000원")
                .contains("월 용역대금 × 4개월");
    }

    @Test
    @DisplayName("직무가 정해지면 산출물 목록이 직군 표준으로 채워진다")
    void deliverablesFollowJobCategory() {
        String development = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), "A", JobRole.BACKEND)).get(2);
        String design = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), "A", JobRole.UX_UI_DESIGNER)).get(2);

        assertThat(development).contains("백엔드 개발자").contains("소스코드").contains("API 명세서");
        assertThat(design).contains("UX·UI 디자이너").contains("디자인 시안").contains("스타일 가이드");
    }

    @Test
    @DisplayName("프로젝트가 지워져 이름·직무가 없어도 계약서가 만들어진다")
    void rendersWithoutProject() {
        // 계약은 5년 보관이라 원본보다 오래 남는다. 열람이 막히면 안 된다.
        List<ContractClause> clauses = ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), null, null);

        Map<Integer, String> content = byNo(clauses);

        assertThat(content.get(1)).contains("(프로젝트명 미상)");
        assertThat(content.get(2)).contains("협의된 직무").contains("산출물 일체");
    }

    @Test
    @DisplayName("상주 계약은 근무 장소가 찍히고, 재택은 별도 문구로 대체된다")
    void workLocationDependsOnWorkStyle() {
        String onsite = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.ONSITE, "서울특별시 강남구 테헤란로 123", null),
                "A", JobRole.BACKEND)).get(7);
        String remote = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, "서울특별시 강남구 테헤란로 123", null),
                "A", JobRole.BACKEND)).get(7);

        assertThat(onsite).contains("상주").contains("테헤란로 123");
        // Contract.create 가 재택이면 근무지를 버린다. 조항도 그에 맞는 문구로 나가야 한다.
        assertThat(remote).contains("재택").doesNotContain("테헤란로").contains("을이 지정하는 장소");
    }

    @Test
    @DisplayName("특약사항이 없으면 빈칸이 아니라 없음으로 적는다")
    void specialTermsFallback() {
        String none = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), "A", JobRole.BACKEND)).get(15);
        String some = byNo(ContractClauseRenderer.render(
                contract(5_000_000L, 4, WorkStyle.REMOTE, null, "산출물은 매주 금요일에 공유한다."),
                "A", JobRole.BACKEND)).get(15);

        assertThat(none).isEqualTo("별도의 특약사항 없음");
        assertThat(some).isEqualTo("산출물은 매주 금요일에 공유한다.");
    }

    @Test
    @DisplayName("조 번호가 중복되지 않고 오름차순이다")
    void clauseNumbersAreOrdered() {
        List<Integer> numbers = ContractClauseRenderer.render(
                        contract(5_000_000L, 4, WorkStyle.REMOTE, null, null), "A", JobRole.BACKEND)
                .stream().map(ContractClause::no).toList();

        assertThat(numbers).doesNotHaveDuplicates().isSorted();
    }

    @Test
    @DisplayName("본문 미리보기")
    void print() {
        ContractClauseRenderer.render(
                        contract(5_000_000L, 4, WorkStyle.ONSITE, "서울특별시 강남구 테헤란로 123",
                                "산출물은 매주 금요일에 공유한다."),
                        "페어링 웹 리뉴얼", JobRole.BACKEND)
                .forEach(c -> System.out.printf("제%d조 (%s)%n%s%n%n", c.no(), c.title(), c.content()));
    }
}
