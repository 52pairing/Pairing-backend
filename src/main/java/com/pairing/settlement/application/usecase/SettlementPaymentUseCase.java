package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.result.SettlementResult;

/** 수수료 결제. PG 연동 없이 상태만 바꾸는 모의 결제다. */
public interface SettlementPaymentUseCase {

    /**
     * 결제 처리. 착수금이면 프로젝트가 모집중으로 넘어간다. (P27)
     *
     * <p>없으면 {@code ST_001}, 납부자가 아니면 {@code ST_002}, 이미 결제됐으면 {@code ST_003}.
     */
    SettlementResult pay(Long settlementId, Long accountId, Long paymentMethodId);
}
