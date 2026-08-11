package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementQueryUseCase {

    /** 납부자 본인만 열람할 수 있다. 없으면 ST_001, 남의 것이면 ST_002. */
    SettlementResult getByIdForPayer(Long settlementId, Long accountId);

    /**
     * 내가 납부자인 정산 목록. projectId / phase / status 는 null 이면 필터하지 않는다.
     *
     * <p>{@code projectId} 는 프로젝트 상세가 그 프로젝트에서 낸 수수료만 보여줄 때 쓴다.
     * 없으면 전체 목록을 받아 프론트가 걸러야 하는데, 페이징에 잘려 정확하지 않다.
     */
    Page<SettlementResult> findMine(Long accountId, Long projectId, SettlementPhase phase,
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
