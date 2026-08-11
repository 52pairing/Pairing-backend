package com.pairing.settlement.domain.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 착수금 수수료 요율. 계약서 제6조에 그대로 찍히는 숫자라 정책 P29·P01 과 어긋나면 안 된다. */
class DepositFeePolicyTest {

    private static final long UNDER = 50_000_000L;
    private static final long OVER = 100_000_000L;

    @Test
    @DisplayName("클라이언트는 1억을 경계로 3%와 2%로 갈린다")
    void clientRateSplitsAtThreshold() {
        assertThat(DepositFeePolicy.feeRate(UNDER)).isEqualByComparingTo("3.00");
        assertThat(DepositFeePolicy.feeRate(OVER)).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("프리랜서는 계약 금액과 무관하게 4%다")
    void freelancerRateIsFlat() {
        // P29: 1억 미만 프리 4% / 1억 이상 프리 4%. 클라이언트와 달리 경계가 없다.
        assertThat(DepositFeePolicy.freelancerFeeRate()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("할인은 다이아·마스터만 1%p 받는다")
    void onlyTopGradesGetDiscount() {
        assertThat(DepositFeePolicy.gradeDiscount(ClientGrade.DIAMOND)).isEqualByComparingTo("1.00");
        assertThat(DepositFeePolicy.gradeDiscount(ClientGrade.SILVER)).isEqualByComparingTo("0.00");

        assertThat(DepositFeePolicy.gradeDiscount(FreelancerGrade.MASTER)).isEqualByComparingTo("1.00");
        assertThat(DepositFeePolicy.gradeDiscount(FreelancerGrade.SENIOR)).isEqualByComparingTo("0.00");
        assertThat(DepositFeePolicy.gradeDiscount(FreelancerGrade.JUNIOR)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("마스터 프리랜서는 4%가 아니라 3%를 낸다")
    void masterPaysDiscountedAmount() {
        BigDecimal rate = DepositFeePolicy.freelancerFeeRate();

        long junior = DepositFeePolicy.feeAmount(20_000_000L, rate,
                DepositFeePolicy.gradeDiscount(FreelancerGrade.JUNIOR));
        long master = DepositFeePolicy.feeAmount(20_000_000L, rate,
                DepositFeePolicy.gradeDiscount(FreelancerGrade.MASTER));

        assertThat(junior).isEqualTo(800_000L);
        assertThat(master).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("원 단위 미만은 버린다")
    void truncatesBelowWon() {
        // 정책에 절사 기준이 없어 내림으로 정했다. 납부자에게 불리해질 일이 없다.
        long fee = DepositFeePolicy.feeAmount(3_333_333L, DepositFeePolicy.freelancerFeeRate(),
                DepositFeePolicy.gradeDiscount(FreelancerGrade.JUNIOR));

        assertThat(fee).isEqualTo(133_333L);
    }
}
