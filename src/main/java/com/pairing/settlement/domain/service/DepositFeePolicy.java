package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 클라이언트 착수금 수수료 계산. (정책 P29 · P01)
 *
 * <p>기준 금액은 프로젝트 예산이다. 착수금은 등록 시점에 발생하는데 그때는 아직 계약이 없다.
 *
 * <p>등급 할인은 비율이 아니라 <b>%p 차감</b>이다. P01 의 "각 수수료 인하(1%)씩 (총 2%)" 는
 * 착수금 1%p 와 성공보수 1%p 를 합쳐 부른 것이라, 착수금에는 1%p 만 적용된다.
 */
public final class DepositFeePolicy {

    /** 이 금액 이상이면 요율이 한 단계 낮아진다. (P29) */
    private static final long HIGH_BUDGET_THRESHOLD = 100_000_000L;

    private static final BigDecimal RATE_UNDER_THRESHOLD = new BigDecimal("3.00");
    private static final BigDecimal RATE_OVER_THRESHOLD = new BigDecimal("2.00");

    private static final BigDecimal DIAMOND_DISCOUNT = new BigDecimal("1.00");
    private static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    private DepositFeePolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static BigDecimal feeRate(long budgetAmount) {
        return budgetAmount >= HIGH_BUDGET_THRESHOLD ? RATE_OVER_THRESHOLD : RATE_UNDER_THRESHOLD;
    }

    /** 실버·골드는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 다이아뿐이다. (P01) */
    public static BigDecimal gradeDiscount(ClientGrade grade) {
        return grade == ClientGrade.DIAMOND ? DIAMOND_DISCOUNT : NO_DISCOUNT;
    }

    /**
     * 수수료(원). 원 단위 미만은 버린다.
     *
     * <p>정책에 절사 기준이 없어 내림으로 정했다. 반올림과 달리 납부자에게 불리해질 일이 없다.
     */
    public static long feeAmount(long budgetAmount, BigDecimal feeRate, BigDecimal gradeDiscount) {
        BigDecimal effectiveRate = feeRate.subtract(gradeDiscount).max(BigDecimal.ZERO);

        return BigDecimal.valueOf(budgetAmount)
                .multiply(effectiveRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
