package com.pairing.settlement.application.command;

import com.pairing.client.domain.model.ClientGrade;

/**
 * 클라이언트 성공보수 정산 생성 입력.
 *
 * <p>등급을 호출부가 넘긴다. 정산 도메인이 client_profile 을 직접 읽지 않기 위해서다.
 *
 * <p>{@code baseAmount} 는 지금 프로젝트 예산이다. 계약 도메인이 붙으면 계약 금액 합계로 바뀐다.
 */
public record CreateSuccessFeeSettlementCommand(
        Long projectId,
        Long payerAccountId,
        long baseAmount,
        ClientGrade clientGrade
) {
}
