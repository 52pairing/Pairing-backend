package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import com.pairing.negotiation.domain.model.NegotiationCondition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 협상 생성 시 "서로 맞지 않는 조건"만 추려낸다(불일치 판정). 후보 추리기가 아니라 값 비교다.
 *
 * <p>규칙(설계 확정):
 * <ul>
 *   <li>AMOUNT: 프리 월단가(환산) &gt; 예산 상한(budgetCap) 이면 불일치</li>
 *   <li>WORK_STYLE/WORK_FORM: 한쪽이라도 ANY 면 일치, 아니면 값이 다를 때 불일치</li>
 *   <li>START_DATE: 어느 한쪽이라도 협의 가능이면 일치, 아니면 프리 착수일이 희망일보다 늦으면 불일치</li>
 *   <li>PERIOD: 프리 스냅샷에 대응값이 없어 제외(조건부). SCOPE/OTHER 는 diff 대상 아님</li>
 * </ul>
 * 값(clientValue/freelancerValue)은 공개 희망값이며 마지노선은 이후 입력받는다.
 */
public final class NegotiationConditionCalculator {

    private NegotiationConditionCalculator() {
    }

    public static List<NegotiationCondition> compute(
            long budgetCap,
            FreelancerConditionSnapshot freelancer,
            Long projectBudgetAmount,
            WorkStyle projectWorkStyle,
            WorkForm projectWorkForm,
            LocalDate projectStartDesiredDate,
            boolean projectStartNegotiable
    ) {
        List<NegotiationCondition> conditions = new ArrayList<>();

        // AMOUNT: 예산 상한(가드) 대비 프리 월단가
        long monthlyPay = freelancer.monthlyPay();
        if (monthlyPay > budgetCap) {
            String clientAmount = String.valueOf(projectBudgetAmount != null ? projectBudgetAmount : budgetCap);
            conditions.add(NegotiationCondition.create(ConditionType.AMOUNT,
                    clientAmount, String.valueOf(monthlyPay), conditions.size()));
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
}
