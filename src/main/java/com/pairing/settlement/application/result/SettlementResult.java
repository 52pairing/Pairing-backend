package com.pairing.settlement.application.result;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 1건.
 *
 * <p>프로젝트명·납부자명은 담지 않는다. 다른 도메인 값이라 응답을 조립하는 쪽이 붙인다.
 *
 * <p>{@code paymentMethodId} 도 같은 이유로 <b>id 만</b> 담는다. "신한카드 **** 1234" 라는
 * 표기는 account 도메인이 만든다.
 */
public record SettlementResult(
        Long settlementId,
        String settlementNo,
        Long projectId,
        Long contractId,
        PartyRole payerRole,
        SettlementPhase phase,
        Long baseAmount,
        BigDecimal feeRate,
        BigDecimal gradeDiscount,
        Long feeAmount,
        SettlementStatus status,
        Long paymentMethodId,
        String approvalNo,
        String failReason,
        String overdueReason,
        LocalDate dueDate,
        boolean payable,
        LocalDateTime paidAt
) {

    public static SettlementResult from(Settlement settlement) {
        return new SettlementResult(
                settlement.getId(),
                settlement.getSettlementNo(),
                settlement.getProjectId(),
                settlement.getContractId(),
                settlement.getPayerRole(),
                settlement.getPhase(),
                settlement.getBaseAmount(),
                settlement.getFeeRate(),
                settlement.getGradeDiscount(),
                settlement.getFeeAmount(),
                settlement.getStatus(),
                settlement.getPaymentMethodId(),
                settlement.getApprovalNo(),
                settlement.getFailReason(),
                settlement.getOverdueReason(),
                settlement.getDueDate(),
                settlement.isPayable(),
                settlement.getPaidAt());
    }
}
