package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.PartyRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 불일치 판정(값 비교) 규칙 단위 검증. Spring 없이 순수 로직만 본다. */
class NegotiationConditionCalculatorTest {

    /** 급여/근무 관련만 지정하는 스냅샷(보조 필드 없음). */
    private FreelancerConditionSnapshot snapshot(PayUnit unit, long amount, WorkStyle style, WorkForm form,
                                                 LocalDate availableFrom, boolean startNegotiable) {
        return new FreelancerConditionSnapshot(unit, amount, style, form, availableFrom, startNegotiable,
                null, null, null);
    }

    private List<NegotiationCondition> compute(long budgetCap, FreelancerConditionSnapshot freelancer,
                                               WorkStyle projectStyle, WorkForm projectForm,
                                               LocalDate projectStart, boolean projectNegotiable) {
        return NegotiationConditionCalculator.compute(budgetCap, freelancer, projectStyle,
                projectForm, projectStart, projectNegotiable, null, null);
    }

    private List<ConditionType> typesOf(List<NegotiationCondition> conditions) {
        return conditions.stream().map(NegotiationCondition::getConditionType).toList();
    }

    @Test
    @DisplayName("완전 일치면 조건이 하나도 안 생긴다")
    void allMatch() {
        List<NegotiationCondition> result = compute(5_000_000L,
                snapshot(PayUnit.MONTHLY, 5_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                        LocalDate.of(2026, 1, 1), false),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("프리 월단가가 예산 상한을 넘으면 AMOUNT 불일치")
    void amountMismatch() {
        List<NegotiationCondition> result = compute(5_000_000L,
                snapshot(PayUnit.MONTHLY, 6_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                WorkStyle.ANY, WorkForm.ANY, null, true);

        assertThat(typesOf(result)).containsExactly(ConditionType.AMOUNT);
        NegotiationCondition amount = result.get(0);
        // 양측 모두 월 단가. 클라 쪽은 예산 상한(budgetCap)이며, 계약 기간 전체 총예산이 아니다.
        assertThat(amount.getClientValue()).isEqualTo("5000000");
        assertThat(amount.getFreelancerValue()).isEqualTo("6000000");
    }

    @Test
    @DisplayName("마지노선은 어느 쪽도 미리 채우지 않는다 — 양측이 직접 입력한다")
    void doesNotPrefillAnyFloor() {
        // 예전에는 minAcceptAmount 를 프리 floor 로 프리필했다. 클라는 직접 입력하는데 프리만
        // 자동으로 채워져 화면이 비대칭이 되고, 그 값을 낮추면 R09 하한 가드가 무력화됐다.
        // 이제 등록 최저 수용가는 마지노선을 받는 시점에 검증한다(NG_012).
        FreelancerConditionSnapshot freelancer = new FreelancerConditionSnapshot(
                PayUnit.MONTHLY, 6_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true,
                5_500_000L, null, null);

        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                4_800_000L, freelancer, WorkStyle.ANY, WorkForm.ANY, null, true, null, null);

        NegotiationCondition amount = result.get(0);
        assertThat(amount.floorForViewer(PartyRole.FREELANCER)).isNull();
        assertThat(amount.floorForViewer(PartyRole.CLIENT)).isNull();
    }

    @Test
    @DisplayName("급여 단위 환산: 일급×20, 시급×160 후 예산과 비교")
    void payConversion() {
        // 일급 30만 × 20 = 600만 > 500만 → 불일치
        List<NegotiationCondition> daily = compute(5_000_000L,
                snapshot(PayUnit.DAILY, 300_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                WorkStyle.ANY, WorkForm.ANY, null, true);
        assertThat(typesOf(daily)).containsExactly(ConditionType.AMOUNT);
        assertThat(daily.get(0).getFreelancerValue()).isEqualTo("6000000");

        // 시급 3만 × 160 = 480만 <= 500만 → 일치
        List<NegotiationCondition> hourly = compute(5_000_000L,
                snapshot(PayUnit.HOURLY, 30_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                WorkStyle.ANY, WorkForm.ANY, null, true);
        assertThat(hourly).isEmpty();
    }

    @Test
    @DisplayName("근무방식/형태: 한쪽이라도 ANY면 일치, 둘 다 구체값이고 다르면 불일치")
    void workStyleAndForm() {
        assertThat(compute(9_000_000L,
                snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.FULL_TIME, null, true),
                WorkStyle.ONSITE, WorkForm.FULL_TIME, null, true)).isEmpty();

        List<NegotiationCondition> result = compute(9_000_000L,
                snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.REMOTE, WorkForm.PART_TIME, null, true),
                WorkStyle.ONSITE, WorkForm.FULL_TIME, null, true);
        assertThat(typesOf(result)).containsExactly(ConditionType.WORK_STYLE, ConditionType.WORK_FORM);
    }

    @Test
    @DisplayName("착수일: 협의 가능이면 일치, 둘 다 고정이고 프리가 더 늦으면 불일치")
    void startDate() {
        assertThat(compute(9_000_000L,
                snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY,
                        LocalDate.of(2026, 3, 1), true),
                WorkStyle.ANY, WorkForm.ANY, LocalDate.of(2026, 1, 1), false)).isEmpty();

        List<NegotiationCondition> result = compute(9_000_000L,
                snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY,
                        LocalDate.of(2026, 3, 1), false),
                WorkStyle.ANY, WorkForm.ANY, LocalDate.of(2026, 1, 1), false);
        assertThat(typesOf(result)).containsExactly(ConditionType.START_DATE);
        assertThat(result.get(0).getClientValue()).isEqualTo("2026-01-01");
        assertThat(result.get(0).getFreelancerValue()).isEqualTo("2026-03-01");
    }

    @Test
    @DisplayName("PERIOD 조건부: 프리 기간값 없으면 제외, 있고 다르면 포함")
    void periodConditional() {
        // 프리 기간 없음(null) → 제외
        FreelancerConditionSnapshot noPeriod = new FreelancerConditionSnapshot(
                PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true, null, null, null);
        List<NegotiationCondition> excluded = NegotiationConditionCalculator.compute(
                9_000_000L, noPeriod, WorkStyle.ANY, WorkForm.ANY, null, true, 6, PeriodUnit.MONTH);
        assertThat(typesOf(excluded)).doesNotContain(ConditionType.PERIOD);

        // 프리 4개월 vs 프로젝트 6개월 → 불일치 포함
        FreelancerConditionSnapshot withPeriod = new FreelancerConditionSnapshot(
                PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true, null, 4, PeriodUnit.MONTH);
        List<NegotiationCondition> included = NegotiationConditionCalculator.compute(
                9_000_000L, withPeriod, WorkStyle.ANY, WorkForm.ANY, null, true, 6, PeriodUnit.MONTH);
        assertThat(typesOf(included)).containsExactly(ConditionType.PERIOD);
        assertThat(included.get(0).getClientValue()).isEqualTo("6 MONTH");
        assertThat(included.get(0).getFreelancerValue()).isEqualTo("4 MONTH");

        // 1개월(=4주) vs 4주 → 정규화하면 같음 → 제외
        FreelancerConditionSnapshot fourWeeks = new FreelancerConditionSnapshot(
                PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true, null, 4, PeriodUnit.WEEK);
        List<NegotiationCondition> sameLength = NegotiationConditionCalculator.compute(
                9_000_000L, fourWeeks, WorkStyle.ANY, WorkForm.ANY, null, true, 1, PeriodUnit.MONTH);
        assertThat(typesOf(sameLength)).doesNotContain(ConditionType.PERIOD);
    }
}
