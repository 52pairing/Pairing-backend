package com.pairing.negotiation.domain.model;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;

/**
 * 매칭 수락 시점에 캡처된 프리랜서 조건 스냅샷(R17/R21/R30, 이후 이력서 수정에 영향받지 않음).
 *
 * <p>협상 조건 diff 대상 필드만 담는다. 경력연차·스킬은 매칭 필터라 제외한다.
 * 급여는 월단가로 환산해 예산(budgetCap)과 비교한다(일급×20, 시급×160).
 */
public record FreelancerConditionSnapshot(
        PayUnit payUnit,
        Long payAmount,          // 원
        WorkStyle workStyle,
        WorkForm workForm,
        LocalDate availableFrom,
        boolean startNegotiable
) {

    private static final long DAYS_PER_MONTH = 20;
    private static final long HOURS_PER_MONTH = 160;

    /** 급여 단위를 월단가(원)로 환산. */
    public long monthlyPay() {
        return switch (payUnit) {
            case MONTHLY -> payAmount;
            case DAILY -> payAmount * DAYS_PER_MONTH;
            case HOURLY -> payAmount * HOURS_PER_MONTH;
        };
    }
}
