package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 착수금 수수료 계산. (정책 P29 · P01)
 *
 * <p>발생 시점이 양측이 다르다. 클라이언트는 <b>프로젝트 등록</b> 시점이고 그때는 아직 계약이 없어
 * 프로젝트 예산을 기준으로 삼는다. 프리랜서는 <b>계약 체결</b> 시점이라 그 계약의 총액을 쓴다.
 *
 * <p>요율도 다르다. 클라이언트만 1억원 경계로 갈리고, 프리랜서는 금액과 무관하게 4% 다.
 *
 * <p>등급 할인은 비율이 아니라 <b>%p 차감</b>이다. P01 의 "각 수수료 인하(1%)씩 (총 2%)" 는
 * 착수금 1%p 와 성공보수 1%p 를 합쳐 부른 것이라, 착수금에는 1%p 만 적용된다.
 */
public final class DepositFeePolicy {

    /** 이 금액 이상이면 클라이언트 요율이 한 단계 낮아진다. 프리랜서는 해당 없다. (P29) */
    private static final long HIGH_BUDGET_THRESHOLD = 100_000_000L;

    private static final BigDecimal RATE_UNDER_THRESHOLD = new BigDecimal("3.00");
    private static final BigDecimal RATE_OVER_THRESHOLD = new BigDecimal("2.00");

    /** 프리랜서 착수금 요율. 1억 미만·이상 모두 4%. (P29) */
    private static final BigDecimal FREELANCER_RATE = new BigDecimal("4.00");

    private static final BigDecimal GRADE_DISCOUNT = new BigDecimal("1.00");
    private static final BigDecimal NO_DISCOUNT = new BigDecimal("0.00");

    private DepositFeePolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static BigDecimal feeRate(long budgetAmount) {
        return budgetAmount >= HIGH_BUDGET_THRESHOLD ? RATE_OVER_THRESHOLD : RATE_UNDER_THRESHOLD;
    }

    /** 프리랜서는 계약 금액과 무관하게 같은 요율이다. 인자를 받지 않는 이유가 그것이다. */
    public static BigDecimal freelancerFeeRate() {
        return FREELANCER_RATE;
    }

    /** 실버·골드는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 다이아뿐이다. (P01) */
    public static BigDecimal gradeDiscount(ClientGrade grade) {
        return grade == ClientGrade.DIAMOND ? GRADE_DISCOUNT : NO_DISCOUNT;
    }

    /** 주니어·시니어는 할인이 없다. 등급 혜택에 수수료 인하가 적힌 등급은 마스터뿐이다. (P01) */
    public static BigDecimal gradeDiscount(FreelancerGrade grade) {
        return grade == FreelancerGrade.MASTER ? GRADE_DISCOUNT : NO_DISCOUNT;
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
