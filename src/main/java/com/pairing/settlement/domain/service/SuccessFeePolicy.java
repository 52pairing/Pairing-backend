package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 클라이언트 성공보수 수수료 계산. (정책 P30 · P01)
 *
 * <p>기준 금액은 프로젝트 예산이다. 원래는 계약 금액이 맞지만 계약 도메인이 아직 없다.
 * 계약이 붙으면 기준 금액만 바꾸면 된다.
 *
 * <p>프리랜서 6% 분은 여기서 만들지 않는다. 누가 프리랜서인지는 계약에서만 알 수 있다.
 *
 * <p>절사 규칙은 착수금과 같은 이유로 내림이다. 두 수수료가 서로 영향받지 않도록
 * {@link DepositFeePolicy} 와 계산을 공유하지 않는다.
 */
public final class SuccessFeePolicy {

    /** 이 금액 이상이면 요율이 한 단계 낮아진다. (P30) */
    private static final long HIGH_BUDGET_THRESHOLD = 100_000_000L;

    private static final BigDecimal RATE_UNDER_THRESHOLD = new BigDecimal("7.00");
    private static final BigDecimal RATE_OVER_THRESHOLD = new BigDecimal("6.00");

    private static final BigDecimal DIAMOND_DISCOUNT = new BigDecimal("1.00");
    private static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    private SuccessFeePolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static BigDecimal feeRate(long baseAmount) {
        return baseAmount >= HIGH_BUDGET_THRESHOLD ? RATE_OVER_THRESHOLD : RATE_UNDER_THRESHOLD;
    }

    /** 실버·골드는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 다이아뿐이다. (P01) */
    public static BigDecimal gradeDiscount(ClientGrade grade) {
        return grade == ClientGrade.DIAMOND ? DIAMOND_DISCOUNT : NO_DISCOUNT;
    }

    /** 수수료(원). 원 단위 미만은 버린다. */
    public static long feeAmount(long baseAmount, BigDecimal feeRate, BigDecimal gradeDiscount) {
        BigDecimal effectiveRate = feeRate.subtract(gradeDiscount).max(BigDecimal.ZERO);

        return BigDecimal.valueOf(baseAmount)
                .multiply(effectiveRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
