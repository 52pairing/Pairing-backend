package com.pairing.matching.application.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.meta.domain.model.PeriodUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
 * <p><b>periodUnit이 WEEK일 때 주→개월 환산 규칙은 아직 팀이 정하지 않았다</b>(.ai/STATE.md
 * "아직 팀 확인 대기 중인 것" 참고). 그 전까지는 4주 = 1개월로 임시 환산한다(올림 처리, 최소 1개월).
 */
@Component
@RequiredArgsConstructor
class BudgetCapCalculator {

    private static final long AMOUNT_TIER_THRESHOLD = 100_000_000L;
    private static final double BASE_RATE_UNDER_TIER = 0.10;
    private static final double BASE_RATE_AT_OR_OVER_TIER = 0.08;
    private static final double DIAMOND_DISCOUNT = 0.02;
    /** 팀 미확정 임시값. WEEK 단위 기간을 개월로 환산할 때만 쓴다. */
    private static final double TEMP_WEEKS_PER_MONTH = 4.0;

    private final ClientGradeResolver clientGradeResolver;

    long calculate(Long projectId, long budgetAmount, int totalHeadcount, int periodValue, PeriodUnit periodUnit) {
        double feeRate = resolveFeeRate(projectId, budgetAmount);
        long netBudget = Math.round(budgetAmount * (1 - feeRate));
        int months = toMonths(periodValue, periodUnit);
        long perHead = totalHeadcount <= 0 ? netBudget : netBudget / totalHeadcount;
        return perHead / months;
    }

    private int toMonths(int periodValue, PeriodUnit periodUnit) {
        if (periodUnit == PeriodUnit.WEEK) {
            return Math.max(1, (int) Math.ceil(periodValue / TEMP_WEEKS_PER_MONTH));
        }
        return Math.max(1, periodValue);
    }

    private double resolveFeeRate(Long projectId, long budgetAmount) {
        double baseRate = budgetAmount >= AMOUNT_TIER_THRESHOLD ? BASE_RATE_AT_OR_OVER_TIER : BASE_RATE_UNDER_TIER;
        ClientGrade grade = clientGradeResolver.resolve(projectId);
        return grade == ClientGrade.DIAMOND ? baseRate - DIAMOND_DISCOUNT : baseRate;
    }
}
