package com.pairing.matching.application.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.settlement.domain.service.DepositFeePolicy;
import com.pairing.settlement.domain.service.SuccessFeePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * budgetCap(협상 상한, 순예산 월단가) 계산.
 *
 * <p>정확한 Stage F 조합 배분 알고리즘은 아직 없어(3일차 예정) "순예산 ÷ 확정 인원수 ÷ 개월 수"로
 * 단순화한다(.ai/STATE.md "확정된 설계 결정 5" 참고). 필드명(budgetCap)은 나중에 정확한 값으로
 * 교체될 때도 그대로 유지한다.
 *
 * <p>budgetAmount는 계약 기간 전체 총액이고(project 도메인 확인, 2026-08-08), 프리랜서 단가는
 * 월단가로 통일하기로 했으므로(설계 결정 3) 총예산을 인원수뿐 아니라 개월 수로도 나눠야 같은
 * 단위로 비교할 수 있다. 이걸 빠뜨리면 협상 쪽(NegotiationConditionCalculator)이 월급을 총예산과
 * 비교하게 되어 상한이 실제보다 수배~수십배 크게 잡힌다.
 *
 * <p>periodUnit이 WEEK일 때 주→개월 환산은 4주 = 1개월로 확정(2026-08-09, 3번 확인 완료, 올림 처리·최소 1개월).
 *
 * <p><b>수수료율은 정산 도메인의 정책을 그대로 쓴다(2026-08-12).</b> 예전엔 여기 10%/8%/다이아 -2%p를
 * 직접 박아뒀는데, 그 값은 {@link DepositFeePolicy}(3%/2%) + {@link SuccessFeePolicy}(7%/6%)의 합을
 * 손으로 옮겨 적은 것이었다. 지금은 우연히 맞지만 정산이 요율을 고치면 매칭만 옛 값으로 남는다 —
 * 그러면 budgetCap이 틀어져 <b>조건점수 단가 20점, 가드 G3 예산 판정, 협상 상한이 한꺼번에</b>
 * 어긋나는데 어디에서도 예외가 나지 않는다. 도메인 경계를 넘는 import보다 단일 출처가 더 중요하다고
 * 판단했다(두 정책 클래스는 I/O 없는 순수 계산이라 결합 비용도 낮다).
 */
@Component
@RequiredArgsConstructor
class BudgetCapCalculator {

    private static final double WEEKS_PER_MONTH = 4.0;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final ClientGradeResolver clientGradeResolver;

    long calculate(Long projectId, long budgetAmount, int totalHeadcount, int periodValue, PeriodUnit periodUnit) {
        long netBudget = toNetBudget(projectId, budgetAmount);
        int months = toMonths(periodValue, periodUnit);
        long perHead = totalHeadcount <= 0 ? netBudget : netBudget / totalHeadcount;
        return perHead / months;
    }

    private int toMonths(int periodValue, PeriodUnit periodUnit) {
        if (periodUnit == PeriodUnit.WEEK) {
            return Math.max(1, (int) Math.ceil(periodValue / WEEKS_PER_MONTH));
        }
        return Math.max(1, periodValue);
    }

    /**
     * 총예산에서 클라이언트가 부담하는 플랫폼 수수료를 뺀 금액.
     *
     * <p>클라이언트는 착수금·성공보수 두 번 낸다. 프리랜서에게 실제로 갈 수 있는 돈은 그 둘을 모두
     * 뺀 나머지이므로 <b>합산 요율</b>로 계산한다. 성공보수의 기준 금액은 원래 계약 총액이지만(1인
     * 계약마다 다르다) 추천 시점엔 계약이 없으므로 프로젝트 예산으로 구간을 판정한다 — 상한 계산에
     * 쓰는 추정값이라 이 정도 근사는 허용된다.
     */
    private long toNetBudget(Long projectId, long budgetAmount) {
        ClientGrade grade = clientGradeResolver.resolve(projectId);

        BigDecimal rate = DepositFeePolicy.feeRate(budgetAmount)
                .add(SuccessFeePolicy.feeRate(budgetAmount))
                .subtract(DepositFeePolicy.gradeDiscount(grade))
                .subtract(SuccessFeePolicy.gradeDiscount(grade))
                .max(BigDecimal.ZERO);

        BigDecimal fee = BigDecimal.valueOf(budgetAmount)
                .multiply(rate)
                .divide(PERCENT, 0, RoundingMode.DOWN);

        return budgetAmount - fee.longValueExact();
    }
}
