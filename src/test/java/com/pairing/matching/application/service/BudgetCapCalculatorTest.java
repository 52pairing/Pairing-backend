package com.pairing.matching.application.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.meta.domain.model.PeriodUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * budgetCap 계산 검증.
 *
 * <p>이 값이 틀어지면 <b>조건점수 단가 20점 · 가드 G3 예산 판정 · 협상 상한</b>이 한꺼번에
 * 어긋나는데, 어디에서도 예외가 나지 않아 발견이 어렵다. 그래서 수수료율을 여기 다시 적지 않고
 * 정산 정책({@code DepositFeePolicy} + {@code SuccessFeePolicy})을 그대로 쓴다 —
 * 아래 테스트는 그 합산이 실제로 반영되는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
class BudgetCapCalculatorTest {

    private static final Long PROJECT_ID = 1L;

    @Mock
    private ClientGradeResolver clientGradeResolver;

    @InjectMocks
    private BudgetCapCalculator budgetCapCalculator;

    @Test
    @DisplayName("1억 미만 실버: 착수금 3% + 성공보수 7% = 10%를 뗀 뒤 인원·개월로 나눈다")
    void appliesCombinedFeeRateUnderThreshold() {
        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.SILVER);

        // 6000만 - 10% = 5400만 ÷ 2명 ÷ 6개월 = 450만
        long cap = budgetCapCalculator.calculate(PROJECT_ID, 60_000_000L, 2, 6, PeriodUnit.MONTH);

        assertThat(cap).isEqualTo(4_500_000L);
    }

    @Test
    @DisplayName("1억 이상이면 요율이 한 단계 낮아진다(2% + 6% = 8%)")
    void appliesLowerFeeRateAtOrOverThreshold() {
        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.SILVER);

        // 1억 - 8% = 9200만 ÷ 2명 ÷ 10개월 = 460만
        long cap = budgetCapCalculator.calculate(PROJECT_ID, 100_000_000L, 2, 10, PeriodUnit.MONTH);

        assertThat(cap).isEqualTo(4_600_000L);
    }

    @Test
    @DisplayName("다이아는 착수금·성공보수 각 1%p씩 총 2%p 할인된다")
    void diamondGetsTwoPercentagePointDiscount() {
        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.DIAMOND);

        // 6000만 - 8%(10 - 2) = 5520만 ÷ 2명 ÷ 6개월 = 460만
        long cap = budgetCapCalculator.calculate(PROJECT_ID, 60_000_000L, 2, 6, PeriodUnit.MONTH);

        assertThat(cap).isEqualTo(4_600_000L);
    }

    @Test
    @DisplayName("골드는 할인이 없다 — 수수료 인하가 정책에 적힌 등급은 다이아뿐이다(P01)")
    void goldHasNoFeeDiscount() {
        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.GOLD);
        long gold = budgetCapCalculator.calculate(PROJECT_ID, 60_000_000L, 2, 6, PeriodUnit.MONTH);

        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.SILVER);
        long silver = budgetCapCalculator.calculate(PROJECT_ID, 60_000_000L, 2, 6, PeriodUnit.MONTH);

        assertThat(gold).isEqualTo(silver);
    }

    @Test
    @DisplayName("기간이 주 단위면 4주=1개월로 올림 환산한다")
    void convertsWeeksToMonths() {
        given(clientGradeResolver.resolve(PROJECT_ID)).willReturn(ClientGrade.SILVER);

        // 24주 = 6개월. 위 첫 테스트와 같은 값이 나와야 한다.
        long cap = budgetCapCalculator.calculate(PROJECT_ID, 60_000_000L, 2, 24, PeriodUnit.WEEK);

        assertThat(cap).isEqualTo(4_500_000L);
    }
}
