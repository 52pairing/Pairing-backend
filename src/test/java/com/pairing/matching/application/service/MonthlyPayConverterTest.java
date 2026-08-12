package com.pairing.matching.application.service;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월단가 환산이 협상 도메인과 같은 값인지 고정한다.
 *
 * <p>어긋나면 매칭이 같은 사람을 협상보다 비싸게(또는 싸게) 보게 되는데, <b>양쪽 다 예외 없이
 * 돌아가서 발견이 어렵다.</b> 그래서 협상 도메인의 실제 계산과 직접 대조한다 — 상수를 여기 다시
 * 적으면 같이 틀려도 통과하기 때문이다.
 */
class MonthlyPayConverterTest {

    @Test
    @DisplayName("협상 도메인(FreelancerConditionSnapshot.monthlyPay)과 값이 같아야 한다")
    void matchesNegotiationDomain() {
        for (PayUnit unit : PayUnit.values()) {
            long amount = 500_000L;
            long negotiation = snapshot(unit, amount).monthlyPay();

            assertThat(MonthlyPayConverter.toMonthlyPay(unit, amount))
                    .as("payUnit=%s", unit)
                    .isEqualTo(negotiation);
        }
    }

    @Test
    @DisplayName("일급 x20 / 시급 x160 / 월급 그대로 (정책 확정값)")
    void appliesPolicyMultipliers() {
        assertThat(MonthlyPayConverter.toMonthlyPay(PayUnit.MONTHLY, 6_000_000L)).isEqualTo(6_000_000L);
        assertThat(MonthlyPayConverter.toMonthlyPay(PayUnit.DAILY, 500_000L)).isEqualTo(10_000_000L);
        assertThat(MonthlyPayConverter.toMonthlyPay(PayUnit.HOURLY, 62_500L)).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("조건이나 급여가 비어 있으면 0 — 합계에 더해도 영향이 없다")
    void returnsZeroWhenConditionMissing() {
        assertThat(MonthlyPayConverter.toMonthlyPay(null)).isZero();
    }

    private static FreelancerConditionSnapshot snapshot(PayUnit payUnit, long payAmount) {
        return new FreelancerConditionSnapshot(payUnit, payAmount, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                null, false, null, 6, PeriodUnit.MONTH);
    }
}
