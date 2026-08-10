package com.pairing.contract.domain.service;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractClause;
import com.pairing.contract.domain.model.ContractDraftText;
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

    private Contract basic() {
        return contract(5_000_000L, 4, WorkStyle.REMOTE, null, null);
    }

    private Map<Integer, String> render(Contract contract, String title, JobRole jobRole,
                                        ContractDraftText draft) {
        return render(contract, title, jobRole, draft, null);
    }

    private Map<Integer, String> render(Contract contract, String title, JobRole jobRole,
                                        ContractDraftText draft, String settlementAccount) {
        return ContractClauseRenderer.render(contract,
                        new ContractClauseRenderer.ClauseContext(title, jobRole, draft, settlementAccount))
                .stream()
                .collect(Collectors.toMap(ContractClause::no, ContractClause::content));
    }

    @Test
    @DisplayName("제4조 금액과 제3조 기간의 개월 수가 서로 맞는다")
    void amountAndPeriodAgree() {
        Map<Integer, String> content = render(basic(), "페어링 웹 리뉴얼", JobRole.BACKEND, null);

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
        String development = render(basic(), "A", JobRole.BACKEND, null).get(2);
        String design = render(basic(), "A", JobRole.UX_UI_DESIGNER, null).get(2);

        assertThat(development).contains("백엔드 개발자").contains("소스코드").contains("API 명세서");
        assertThat(design).contains("UX·UI 디자이너").contains("디자인 시안").contains("스타일 가이드");
    }

    @Test
    @DisplayName("AI 문구가 없으면 제2조는 직무·산출물·일반 문구 3항이다")
    void scopeWithoutDraft() {
        String clause = render(basic(), "A", JobRole.BACKEND, null).get(2);

        assertThat(clause)
                .contains("① 을이 수행할 직무는")
                .contains("② 을이 제출할 산출물은")
                .contains("③ 세부 업무 범위는 갑이 제공한 과업 내용")
                .doesNotContain("④");
    }

    @Test
    @DisplayName("AI 문구가 있으면 담당 업무 항이 끼고 뒤 항 번호가 밀린다")
    void scopeWithDraft() {
        ContractDraftText draft = new ContractDraftText(
                "모바일 앱용 RESTful API 설계 및 개발",
                "Node.js 기반 API 구현, 데이터베이스 스키마 설계",
                "별도의 특약사항 없음");

        String clause = render(basic(), "A", JobRole.BACKEND, draft).get(2);

        assertThat(clause)
                .contains("① 을이 수행할 직무는")
                .contains("② 을이 수행할 주요 업무는 다음과 같다. 모바일 앱용 RESTful API 설계 및 개발")
                .contains("③ 을이 제출할 산출물은")
                .contains("④ 세부 업무 범위는 Node.js 기반 API 구현");
    }

    @Test
    @DisplayName("세부 업무 범위 원문이 없어 AI 가 빈 값을 주면 일반 문구로 되돌아간다")
    void scopeFallsBackWhenDetailScopeIsEmpty() {
        // 파이썬은 원문이 없으면 요약도 빈 문자열로 준다. 없는 업무를 지어내지 않기 위해서다.
        ContractDraftText draft = new ContractDraftText("API 설계 및 개발", "", "별도의 특약사항 없음");

        String clause = render(basic(), "A", JobRole.BACKEND, draft).get(2);

        assertThat(clause)
                .contains("② 을이 수행할 주요 업무는")
                .contains("④ 세부 업무 범위는 갑이 제공한 과업 내용");
    }

    @Test
    @DisplayName("프로젝트가 지워져 이름·직무가 없어도 계약서가 만들어진다")
    void rendersWithoutProject() {
        // 계약은 5년 보관이라 원본보다 오래 남는다. 열람이 막히면 안 된다.
        Map<Integer, String> content = render(basic(), null, null, null);

        assertThat(content.get(1)).contains("(프로젝트명 미상)");
        assertThat(content.get(2)).contains("협의된 직무").contains("산출물 일체");
    }

    @Test
    @DisplayName("상주 계약은 근무 장소가 찍히고, 재택은 별도 문구로 대체된다")
    void workLocationDependsOnWorkStyle() {
        String address = "서울특별시 강남구 테헤란로 123";
        String onsite = render(contract(5_000_000L, 4, WorkStyle.ONSITE, address, null),
                "A", JobRole.BACKEND, null).get(7);
        String remote = render(contract(5_000_000L, 4, WorkStyle.REMOTE, address, null),
                "A", JobRole.BACKEND, null).get(7);

        assertThat(onsite).contains("상주").contains("테헤란로 123");
        // Contract.create 가 재택이면 근무지를 버린다. 조항도 그에 맞는 문구로 나가야 한다.
        assertThat(remote).contains("재택").doesNotContain("테헤란로").contains("을이 지정하는 장소");
    }

    @Test
    @DisplayName("특약사항이 없으면 빈칸이 아니라 없음으로 적는다")
    void specialTermsFallback() {
        String none = render(basic(), "A", JobRole.BACKEND, null).get(15);
        String some = render(contract(5_000_000L, 4, WorkStyle.REMOTE, null,
                "산출물은 매주 금요일에 공유한다."), "A", JobRole.BACKEND, null).get(15);

        assertThat(none).isEqualTo("별도의 특약사항 없음");
        assertThat(some).isEqualTo("산출물은 매주 금요일에 공유한다.");
    }

    @Test
    @DisplayName("제1조부터 제15조까지 빠짐없이 오름차순으로 나온다")
    void clauseNumbersAreComplete() {
        List<Integer> numbers = ContractClauseRenderer.render(basic(),
                        new ContractClauseRenderer.ClauseContext("A", JobRole.BACKEND, null, null))
                .stream().map(ContractClause::no).toList();

        assertThat(numbers).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    @Test
    @DisplayName("정산 계좌가 있으면 제5조에 찍고, 없으면 일반 문구로 둔다")
    void paymentAccount() {
        String withAccount = render(basic(), "A", JobRole.BACKEND, null,
                "카카오뱅크 3333012345678 (예금주: 김민준)").get(5);
        String without = render(basic(), "A", JobRole.BACKEND, null).get(5);

        assertThat(withAccount).contains("지급 계좌는 카카오뱅크 3333012345678 (예금주: 김민준) 로 한다.");
        assertThat(without).contains("을이 플랫폼에 등록한 정산 계좌로 한다.");
    }

    @Test
    @DisplayName("검수 조항에 하자 보완 요청 항이 들어간다")
    void inspectionCoversDefects() {
        String clause = render(basic(), "A", JobRole.BACKEND, null).get(8);

        assertThat(clause)
                .contains("③ 검수를 통과하면")
                .contains("④ 하자가 발생한 경우 갑은 7일 이내에 서면으로 보완을 요청할 수 있다.");
    }

    @Test
    @DisplayName("비밀유지 기간과 위약금율은 계약이 들고 있는 값으로 찍는다")
    void fixedClausesUseContractValues() {
        Map<Integer, String> content = render(basic(), "A", JobRole.BACKEND, null);

        // 기본값 confidential_years = 3, penalty_rate = 10.00
        assertThat(content.get(10)).contains("계약 종료 후 3년간");
        // numeric(5,2) 라 10.00 으로 들어오는데 계약서에는 "10%" 로 나가야 한다.
        assertThat(content.get(12)).contains("10%").doesNotContain("10.00");
    }

    @Test
    @DisplayName("파기 위약금은 상대방과 플랫폼 양쪽을 모두 적는다")
    void terminationCoversBothPayees() {
        // 정책 P32: 파기 주체가 상대방 10% + 플랫폼 10% 를 부담한다.
        String clause = render(basic(), "A", JobRole.BACKEND, null).get(12);

        assertThat(clause)
                .contains("14일 이내에 시정하지 않는 경우")
                .contains("② 을이 파기하는 경우")
                .contains("③ 갑이 파기하는 경우")
                .contains("상대방에게")
                .contains("플랫폼에");
    }
}
