package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.PartyRole;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 협상 생성 시 "서로 맞지 않는 조건"만 추려낸다(불일치 판정). 후보 추리기가 아니라 값 비교다.
 *
 * <p>규칙(설계 확정):
 * <ul>
 *   <li>AMOUNT: 프리 월단가(환산) &gt; 예산 상한(budgetCap) 이면 불일치. 프리 minAcceptAmount(가드 하한)를
 *       프리 floor 로 프리필한다.</li>
 *   <li>WORK_STYLE/WORK_FORM: 한쪽이라도 ANY 면 일치, 아니면 값이 다를 때 불일치</li>
 *   <li>START_DATE: 어느 한쪽이라도 협의 가능이면 일치, 아니면 프리 착수일이 희망일보다 늦으면 불일치</li>
 *   <li>PERIOD: 조건부 — 프리 기간값이 있을 때만 비교(주 단위 정규화)해 다르면 불일치. 없으면 제외.</li>
 * </ul>
 * 값(clientValue/freelancerValue)은 공개 희망값이며 마지노선(floor)은 비공개다.
 */
public final class NegotiationConditionCalculator {

    private static final int WEEKS_PER_MONTH = 4;

    private NegotiationConditionCalculator() {
    }

    public static List<NegotiationCondition> compute(
            long budgetCap,
            FreelancerConditionSnapshot freelancer,
            WorkStyle projectWorkStyle,
            WorkForm projectWorkForm,
            LocalDate projectStartDesiredDate,
            boolean projectStartNegotiable,
            Integer projectPeriodValue,
            PeriodUnit projectPeriodUnit
    ) {
        List<NegotiationCondition> conditions = new ArrayList<>();

        // AMOUNT: 예산 상한(가드) 대비 프리 월단가. minAcceptAmount 를 프리 floor 로 프리필.
        //
        // 클라 희망값도 budgetCap(월 단가 상한)을 쓴다. 예전엔 projectBudgetAmount(계약 기간 전체 총액)를
        // 넣어 한 조건 안에서 클라=총액 / 프리=월단가로 단위가 갈렸고, 그 사이에서 합의된 값은 어느
        // 단위인지 정의되지 않았다. 협상·화면·계약 모두 월 단가로 통일한다.
        long monthlyPay = freelancer.monthlyPay();
        if (monthlyPay > budgetCap) {
            NegotiationCondition amount = NegotiationCondition.create(ConditionType.AMOUNT,
                    String.valueOf(budgetCap), String.valueOf(monthlyPay), conditions.size());
            if (freelancer.minAcceptAmount() != null) {
                amount.submitFloor(PartyRole.FREELANCER, String.valueOf(freelancer.minAcceptAmount()));
            }
            conditions.add(amount);
        }

        // WORK_STYLE
        if (isMismatch(projectWorkStyle, freelancer.workStyle())) {
            conditions.add(NegotiationCondition.create(ConditionType.WORK_STYLE,
                    projectWorkStyle.name(), freelancer.workStyle().name(), conditions.size()));
        }

        // WORK_FORM
        if (isMismatch(projectWorkForm, freelancer.workForm())) {
            conditions.add(NegotiationCondition.create(ConditionType.WORK_FORM,
                    projectWorkForm.name(), freelancer.workForm().name(), conditions.size()));
        }

        // START_DATE
        if (isStartMismatch(projectStartNegotiable, projectStartDesiredDate,
                freelancer.startNegotiable(), freelancer.availableFrom())) {
            conditions.add(NegotiationCondition.create(ConditionType.START_DATE,
                    String.valueOf(projectStartDesiredDate), String.valueOf(freelancer.availableFrom()),
                    conditions.size()));
        }

        // PERIOD (조건부): 프리 기간값이 있을 때만
        if (isPeriodMismatch(projectPeriodValue, projectPeriodUnit, freelancer)) {
            conditions.add(NegotiationCondition.create(ConditionType.PERIOD,
                    periodLabel(projectPeriodValue, projectPeriodUnit),
                    periodLabel(freelancer.periodValue(), freelancer.periodUnit()), conditions.size()));
        }

        return conditions;
    }

    /** 한쪽이라도 ANY(모두 가능)면 일치. 둘 다 구체값이고 다르면 불일치. */
    private static boolean isMismatch(WorkStyle project, WorkStyle freelancer) {
        if (project == null || freelancer == null || project == WorkStyle.ANY || freelancer == WorkStyle.ANY) {
            return false;
        }
        return project != freelancer;
    }

    private static boolean isMismatch(WorkForm project, WorkForm freelancer) {
        if (project == null || freelancer == null || project == WorkForm.ANY || freelancer == WorkForm.ANY) {
            return false;
        }
        return project != freelancer;
    }

    /** 어느 한쪽이라도 협의 가능이면 일치. 둘 다 고정이고 프리 착수일이 희망일보다 늦으면 불일치. */
    private static boolean isStartMismatch(boolean projectNegotiable, LocalDate projectDesired,
                                           boolean freelancerNegotiable, LocalDate freelancerAvailable) {
        if (projectNegotiable || freelancerNegotiable || projectDesired == null || freelancerAvailable == null) {
            return false;
        }
        return freelancerAvailable.isAfter(projectDesired);
    }

    /** 프리 기간값이 있을 때만 비교(주 단위 정규화). 프리 기간 없거나 프로젝트 기간 없으면 제외. */
    private static boolean isPeriodMismatch(Integer projectPeriodValue, PeriodUnit projectPeriodUnit,
                                            FreelancerConditionSnapshot freelancer) {
        if (!freelancer.hasPeriod() || projectPeriodValue == null || projectPeriodUnit == null) {
            return false;
        }
        return toWeeks(projectPeriodValue, projectPeriodUnit) != freelancer.periodInWeeks();
    }

    private static int toWeeks(int value, PeriodUnit unit) {
        return unit == PeriodUnit.MONTH ? value * WEEKS_PER_MONTH : value;
    }

    private static String periodLabel(Integer value, PeriodUnit unit) {
        return value + " " + unit.name();
    }
}
