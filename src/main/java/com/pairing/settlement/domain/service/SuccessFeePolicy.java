package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 성공보수 수수료 계산. (정책 P30 · P01)
 *
 * <pre>
 *   클라이언트   1억 미만 7% / 1억 이상 6%   기준 금액: 프로젝트 예산
 *   프리랜서     구간 무관 6%                기준 금액: 그 프리랜서의 계약 총액
 * </pre>
 *
 * <p>프리랜서가 단일 요율인 것은 착수금(4%)과 같다. 기준 금액이 프로젝트 예산이 아니라
 * <b>계약 총액</b>인 이유도 같다 — 여러 명을 뽑으면 각자 계약 금액이 달라 예산으로는 나눌 수 없다.
 *
 * <p>등급 할인은 최상위 등급만 1%p 다. 클라이언트는 다이아, 프리랜서는 마스터이며
 * "착수금·성공보수 각 1%씩(총 2%)"이 등급 혜택이다. (P01)
 *
 * <p>절사 규칙은 착수금과 같은 이유로 내림이다. 두 수수료가 서로 영향받지 않도록
 * {@link DepositFeePolicy} 와 계산을 공유하지 않는다.
 */
public final class SuccessFeePolicy {

    /** 이 금액 이상이면 요율이 한 단계 낮아진다. (P30) */
    private static final long HIGH_BUDGET_THRESHOLD = 100_000_000L;

    private static final BigDecimal RATE_UNDER_THRESHOLD = new BigDecimal("7.00");
    private static final BigDecimal RATE_OVER_THRESHOLD = new BigDecimal("6.00");

    /** 프리랜서는 금액 구간과 무관하게 6% 단일이다. 착수금 4% 와 같은 구조다. (P30) */
    private static final BigDecimal FREELANCER_RATE = new BigDecimal("6.00");

    private static final BigDecimal GRADE_DISCOUNT = new BigDecimal("1.00");
    private static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    private SuccessFeePolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static BigDecimal feeRate(long baseAmount) {
        return baseAmount >= HIGH_BUDGET_THRESHOLD ? RATE_OVER_THRESHOLD : RATE_UNDER_THRESHOLD;
    }

    /** 프리랜서 요율. 금액 구간을 보지 않아 인자가 없다. */
    public static BigDecimal freelancerFeeRate() {
        return FREELANCER_RATE;
    }

    /** 실버·골드는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 다이아뿐이다. (P01) */
    public static BigDecimal gradeDiscount(ClientGrade grade) {
        return grade == ClientGrade.DIAMOND ? GRADE_DISCOUNT : NO_DISCOUNT;
    }

    /** 주니어·시니어는 할인이 없다. 수수료 인하가 적힌 등급은 마스터뿐이다. (P01) */
    public static BigDecimal gradeDiscount(FreelancerGrade grade) {
        return grade == FreelancerGrade.MASTER ? GRADE_DISCOUNT : NO_DISCOUNT;
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
