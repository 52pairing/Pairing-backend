package com.pairing.matching.application.service;

import com.pairing.client.domain.model.ClientGrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * budgetCap(협상 상한, 순예산 월단가) 계산.
 *
 * <p>정확한 Stage F 조합 배분 알고리즘은 아직 없어(3일차 예정) "순예산 ÷ 확정 인원수"로 단순화한다
 * (.ai/STATE.md "확정된 설계 결정 5" 참고). 필드명(budgetCap)은 나중에 정확한 값으로 교체될 때도 그대로 유지한다.
 */
@Component
@RequiredArgsConstructor
class BudgetCapCalculator {

    private static final long AMOUNT_TIER_THRESHOLD = 100_000_000L;
    private static final double BASE_RATE_UNDER_TIER = 0.10;
    private static final double BASE_RATE_AT_OR_OVER_TIER = 0.08;
    private static final double DIAMOND_DISCOUNT = 0.02;

    private final ClientGradeResolver clientGradeResolver;

    long calculate(Long projectId, long budgetAmount, int confirmedHeadcount) {
        double feeRate = resolveFeeRate(projectId, budgetAmount);
        long netBudget = Math.round(budgetAmount * (1 - feeRate));
        return confirmedHeadcount <= 0 ? netBudget : netBudget / confirmedHeadcount;
    }

    private double resolveFeeRate(Long projectId, long budgetAmount) {
        double baseRate = budgetAmount >= AMOUNT_TIER_THRESHOLD ? BASE_RATE_AT_OR_OVER_TIER : BASE_RATE_UNDER_TIER;
        ClientGrade grade = clientGradeResolver.resolve(projectId);
        return grade == ClientGrade.DIAMOND ? baseRate - DIAMOND_DISCOUNT : baseRate;
    }
}
