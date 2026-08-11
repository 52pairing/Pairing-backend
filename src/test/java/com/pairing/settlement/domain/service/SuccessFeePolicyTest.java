package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 성공보수 수수료 요율. 계약서 제6조에 그대로 찍히는 숫자라 정책 P30·P01 과 어긋나면 안 된다. */
class SuccessFeePolicyTest {

    private static final long UNDER = 50_000_000L;
    private static final long OVER = 100_000_000L;

    @Test
    @DisplayName("클라이언트는 1억을 경계로 7%와 6%로 갈린다")
    void clientRateSplitsAtThreshold() {
        assertThat(SuccessFeePolicy.feeRate(UNDER)).isEqualByComparingTo("7.00");
        assertThat(SuccessFeePolicy.feeRate(OVER)).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("프리랜서는 계약 금액과 무관하게 6%다")
    void freelancerRateIsFlat() {
        // P30: 1억 미만 프리 6% / 1억 이상 프리 6%. 클라이언트와 달리 경계가 없다.
        assertThat(SuccessFeePolicy.freelancerFeeRate()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("최상위 등급만 1%p 를 깎는다")
    void onlyTopGradeGetsDiscount() {
        // P01 등급 혜택: "착수금·성공보수 각 수수료 인하(1%)씩 (총 2%)". 그 아래 등급에는 혜택이 없다.
        assertThat(SuccessFeePolicy.gradeDiscount(ClientGrade.DIAMOND)).isEqualByComparingTo("1.00");
        assertThat(SuccessFeePolicy.gradeDiscount(ClientGrade.GOLD)).isEqualByComparingTo("0.00");
        assertThat(SuccessFeePolicy.gradeDiscount(ClientGrade.SILVER)).isEqualByComparingTo("0.00");

        assertThat(SuccessFeePolicy.gradeDiscount(FreelancerGrade.MASTER)).isEqualByComparingTo("1.00");
        assertThat(SuccessFeePolicy.gradeDiscount(FreelancerGrade.SENIOR)).isEqualByComparingTo("0.00");
        assertThat(SuccessFeePolicy.gradeDiscount(FreelancerGrade.JUNIOR)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("등급 할인은 요율에서 빼고 계산한다")
    void appliesGradeDiscount() {
        // 계약 2,000만 · 마스터 프리랜서 → 6% - 1% = 5% → 100만원
        long feeAmount = SuccessFeePolicy.feeAmount(20_000_000L,
                SuccessFeePolicy.freelancerFeeRate(),
                SuccessFeePolicy.gradeDiscount(FreelancerGrade.MASTER));

        assertThat(feeAmount).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("할인이 없으면 그대로 계산한다")
    void appliesFullRateWithoutDiscount() {
        // 계약 2,000만 · 주니어 → 6% → 120만원
        long feeAmount = SuccessFeePolicy.feeAmount(20_000_000L,
                SuccessFeePolicy.freelancerFeeRate(),
                SuccessFeePolicy.gradeDiscount(FreelancerGrade.JUNIOR));

        assertThat(feeAmount).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("원 단위 미만은 버린다")
    void truncatesToWon() {
        // 3,333,333 × 6% = 199,999.98 → 199,999
        assertThat(SuccessFeePolicy.feeAmount(3_333_333L,
                SuccessFeePolicy.freelancerFeeRate(), BigDecimal.ZERO))
                .isEqualTo(199_999L);
    }
}
