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

    /**
     * 아직 내지 않은 정산이 하나라도 있는지. 회원 탈퇴 가능 여부 판정에 쓴다.
     *
     * <p>PENDING · OVERDUE · FAILED 를 미결제로 본다. 결제 실패는 다시 시도할 수 있어
     * 아직 내지 않은 돈이다. CANCELED 는 낼 이유가 사라진 건이라 제외한다.
     *
     * <p>위약금은 아직 도메인이 없어 보지 않는다. 생기면 여기에 조건을 더한다.
     */
    boolean hasUnpaidSettlement(Long accountId);
}
