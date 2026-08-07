package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 불일치 판정(값 비교) 규칙 단위 검증. Spring 없이 순수 로직만 본다. */
class NegotiationConditionCalculatorTest {

    private FreelancerConditionSnapshot snapshot(PayUnit unit, long amount, WorkStyle style, WorkForm form,
                                                 LocalDate availableFrom, boolean startNegotiable) {
        return new FreelancerConditionSnapshot(unit, amount, style, form, availableFrom, startNegotiable);
    }

    private List<ConditionType> typesOf(List<NegotiationCondition> conditions) {
        return conditions.stream().map(NegotiationCondition::getConditionType).toList();
    }

    @Test
    @DisplayName("완전 일치면 조건이 하나도 안 생긴다")
    void allMatch() {
        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                5_000_000L,
                snapshot(PayUnit.MONTHLY, 5_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                        LocalDate.of(2026, 1, 1), false),
                5_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("프리 월단가가 예산 상한을 넘으면 AMOUNT 불일치")
    void amountMismatch() {
        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                5_000_000L,
                snapshot(PayUnit.MONTHLY, 6_000_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                4_800_000L, WorkStyle.ANY, WorkForm.ANY, null, true);

        assertThat(typesOf(result)).containsExactly(ConditionType.AMOUNT);
        NegotiationCondition amount = result.get(0);
        assertThat(amount.getClientValue()).isEqualTo("4800000");   // 등록 예산(표시)
        assertThat(amount.getFreelancerValue()).isEqualTo("6000000");
    }

    @Test
    @DisplayName("급여 단위 환산: 일급×20, 시급×160 후 예산과 비교")
    void payConversion() {
        // 일급 30만 × 20 = 600만 > 500만 → 불일치
        List<NegotiationCondition> daily = NegotiationConditionCalculator.compute(
                5_000_000L, snapshot(PayUnit.DAILY, 300_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                null, WorkStyle.ANY, WorkForm.ANY, null, true);
        assertThat(typesOf(daily)).containsExactly(ConditionType.AMOUNT);
        assertThat(daily.get(0).getFreelancerValue()).isEqualTo("6000000");

        // 시급 3만 × 160 = 480만 <= 500만 → 일치
        List<NegotiationCondition> hourly = NegotiationConditionCalculator.compute(
                5_000_000L, snapshot(PayUnit.HOURLY, 30_000L, WorkStyle.ANY, WorkForm.ANY, null, true),
                null, WorkStyle.ANY, WorkForm.ANY, null, true);
        assertThat(hourly).isEmpty();
    }

    @Test
    @DisplayName("근무방식/형태: 한쪽이라도 ANY면 일치, 둘 다 구체값이고 다르면 불일치")
    void workStyleAndForm() {
        // ANY → 일치
        assertThat(NegotiationConditionCalculator.compute(
                9_000_000L, snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.FULL_TIME, null, true),
                null, WorkStyle.ONSITE, WorkForm.FULL_TIME, null, true)).isEmpty();

        // 재택 vs 상주 → 불일치, 풀타임 vs 파트타임 → 불일치
        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                9_000_000L, snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.REMOTE, WorkForm.PART_TIME, null, true),
                null, WorkStyle.ONSITE, WorkForm.FULL_TIME, null, true);
        assertThat(typesOf(result)).containsExactly(ConditionType.WORK_STYLE, ConditionType.WORK_FORM);
    }

    @Test
    @DisplayName("착수일: 협의 가능이면 일치, 둘 다 고정이고 프리가 더 늦으면 불일치")
    void startDate() {
        // 프리 협의 가능 → 일치
        assertThat(NegotiationConditionCalculator.compute(
                9_000_000L, snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY,
                        LocalDate.of(2026, 3, 1), true),
                null, WorkStyle.ANY, WorkForm.ANY, LocalDate.of(2026, 1, 1), false)).isEmpty();

        // 둘 다 고정, 프리 착수일이 더 늦음 → 불일치
        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                9_000_000L, snapshot(PayUnit.MONTHLY, 1_000_000L, WorkStyle.ANY, WorkForm.ANY,
                        LocalDate.of(2026, 3, 1), false),
                null, WorkStyle.ANY, WorkForm.ANY, LocalDate.of(2026, 1, 1), false);
        assertThat(typesOf(result)).containsExactly(ConditionType.START_DATE);
        assertThat(result.get(0).getClientValue()).isEqualTo("2026-01-01");
        assertThat(result.get(0).getFreelancerValue()).isEqualTo("2026-03-01");
    }

    @Test
    @DisplayName("PERIOD는 diff 대상이 아니다(조건부 제외)")
    void periodExcluded() {
        List<NegotiationCondition> result = NegotiationConditionCalculator.compute(
                1L, snapshot(PayUnit.MONTHLY, 9_999_999L, WorkStyle.REMOTE, WorkForm.PART_TIME,
                        LocalDate.of(2027, 1, 1), false),
                null, WorkStyle.ONSITE, WorkForm.FULL_TIME, LocalDate.of(2026, 1, 1), false);

        assertThat(typesOf(result)).doesNotContain(ConditionType.PERIOD);
    }
}
