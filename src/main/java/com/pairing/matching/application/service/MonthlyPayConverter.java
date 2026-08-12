package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.meta.domain.model.PayUnit;

/**
 * 프리랜서 희망 급여를 월 단가(원)로 환산한다.
 *
 * <p><b>일급 ×20, 시급 ×160은 정책 확정값이다</b>(`.ai/STATE.md` "확정된 설계 결정 3").
 * 협상 도메인의 {@code FreelancerConditionSnapshot.monthlyPay()}와 <b>반드시 같은 값이어야 한다</b> —
 * 어긋나면 매칭이 같은 사람을 협상보다 비싸게(또는 싸게) 보게 되는데, <b>양쪽 다 에러 없이
 * 돌아가서 발견이 어렵다.</b> Pairing-python의 {@code matching/scoring.py}도 같은 값을 쓴다.
 *
 * <p>협상 쪽 메서드를 그대로 쓰지 않는 이유: {@code FreelancerConditionSnapshot}은 협상 생성용
 * 레코드라 매칭이 갖고 있지 않은 필드(minAcceptAmount 등)까지 요구한다. 환산식 자체는 한 줄이라
 * 여기서 다시 쓰되, 값이 어긋나지 않게 테스트로 고정한다
 * ({@code MonthlyPayConverterTest.matchesNegotiationDomain}).
 *
 * <p>{@code freelancer_condition.pay_amount}는 <b>원 단위 저장</b>이다. 화면만 만원 단위로 받고
 * 서버는 만원 배수인지만 검증한다({@code FreelancerCondition.requireInTenThousandUnit} 주석).
 * 만원으로 착각해 10000을 곱하면 전원이 예산 초과로 잡힌다.
 */
final class MonthlyPayConverter {

    private static final long DAYS_PER_MONTH = 20;
    private static final long HOURS_PER_MONTH = 160;

    private MonthlyPayConverter() {
    }

    /** 조건을 못 읽었거나 급여가 비어 있으면 0. 합계에 더해도 영향이 없다. */
    static long toMonthlyPay(FreelancerConditionResponse condition) {
        if (condition == null || condition.payUnit() == null || condition.payAmount() == null) {
            return 0L;
        }
        return toMonthlyPay(condition.payUnit(), condition.payAmount());
    }

    static long toMonthlyPay(PayUnit payUnit, long payAmount) {
        return switch (payUnit) {
            case MONTHLY -> payAmount;
            case DAILY -> payAmount * DAYS_PER_MONTH;
            case HOURLY -> payAmount * HOURS_PER_MONTH;
        };
    }
}
