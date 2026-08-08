package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementQueryUseCase {

    /** 납부자 본인만 열람할 수 있다. 없으면 ST_001, 남의 것이면 ST_002. */
    SettlementResult getByIdForPayer(Long settlementId, Long accountId);

    /** 내가 납부자인 정산 목록. phase / status 는 null 이면 필터하지 않는다. */
    Page<SettlementResult> findMine(Long accountId, SettlementPhase phase,
                                    SettlementStatus status, Pageable pageable);
}
