package com.pairing.matching.application.command;

import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;

/**
 * 매칭 요청 수락 시 협상방 생성을 위해 negotiation 도메인에 넘기는 값.
 *
 * <p>필드는 2026-08-07 확정한 계약 그대로다(diff 대상 4개 + 보조 2개, PERIOD는 조건부 포함).
 * {@code periodValue}가 null이면 이 협상에서는 기간을 diff 대상에서 뺀다는 뜻이다.
 */
public record CreateNegotiationCommand(
        Long requestId,
        Long projectId,
        Long positionId,
        Long freelancerId,
        Long budgetCap,
        PayUnit payUnit,
        Long payAmount,
        WorkStyle workStyle,
        WorkForm workForm,
        LocalDate availableFrom,
        Long minAcceptAmount,
        boolean startNegotiable,
        Integer periodValue,
        PeriodUnit periodUnit
) {
}
