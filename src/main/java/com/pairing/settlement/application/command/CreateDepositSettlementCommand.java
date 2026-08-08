package com.pairing.settlement.application.command;

import com.pairing.client.domain.model.ClientGrade;

/**
 * 클라이언트 착수금 정산 생성 입력.
 *
 * <p>등급을 호출부가 넘긴다. 정산 도메인이 client_profile 을 직접 읽지 않기 위해서다.
 */
public record CreateDepositSettlementCommand(
        Long projectId,
        Long payerAccountId,
        long budgetAmount,
        ClientGrade clientGrade
) {
}
