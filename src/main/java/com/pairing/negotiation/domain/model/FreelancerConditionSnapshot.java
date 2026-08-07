package com.pairing.negotiation.domain.model;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;

/**
 * 매칭 수락 시점에 캡처된 프리랜서 조건 스냅샷(R17/R21/R30, 이후 이력서 수정에 영향받지 않음).
 *
 * <p>확정 필드 = diff 대상 4 + 보조 2.
 * <ul>
 *   <li>diff 대상: 급여(payUnit+payAmount), workStyle, workForm, availableFrom</li>
 *   <li>보조: minAcceptAmount(R09 금액 가드 하한, diff 아님 → AMOUNT 프리 floor 로 프리필),
 *       기간(periodValue/periodUnit, 조건부 — periodValue 있으면 diff 포함, 없으면 제외)</li>
 * </ul>
 * 급여는 월단가로 환산해 예산(budgetCap)과 비교한다(일급×20, 시급×160).
 */
public record FreelancerConditionSnapshot(
        PayUnit payUnit,
        Long payAmount,          // 원
        WorkStyle workStyle,
        WorkForm workForm,
        LocalDate availableFrom,
        boolean startNegotiable,
        Long minAcceptAmount,    // R09 금액 가드 하한(원, nullable). 절대 월단가 값으로 간주(payUnit 환산 안 함)
        Integer periodValue,     // 희망 기간(nullable — 없으면 PERIOD diff 제외)
        PeriodUnit periodUnit
) {

    private static final long DAYS_PER_MONTH = 20;
    private static final long HOURS_PER_MONTH = 160;
    private static final int WEEKS_PER_MONTH = 4;

    /** 급여 단위를 월단가(원)로 환산. */
    public long monthlyPay() {
        return switch (payUnit) {
            case MONTHLY -> payAmount;
            case DAILY -> payAmount * DAYS_PER_MONTH;
            case HOURLY -> payAmount * HOURS_PER_MONTH;
        };
    }

    /** 기간 diff 대상 여부(선택 입력이라 없을 수 있음). */
    public boolean hasPeriod() {
        return periodValue != null && periodUnit != null;
    }

    /** 기간을 주 단위로 정규화(단위 달라도 비교 가능하게). hasPeriod() 전제. */
    public int periodInWeeks() {
        return periodUnit == PeriodUnit.MONTH ? periodValue * WEEKS_PER_MONTH : periodValue;
    }
}
