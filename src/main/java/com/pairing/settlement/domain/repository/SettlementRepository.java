package com.pairing.settlement.domain.repository;

import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long settlementId);

    /** 내 정산 목록. phase / status 는 null 이면 필터하지 않는다. */
    Page<Settlement> findByPayer(Long payerAccountId, SettlementPhase phase,
                                 SettlementStatus status, Pageable pageable);

    /**
     * 프로젝트에 걸린 결제 대기 정산 1건.
     *
     * <p>화면의 결제 버튼이 쓴다. 착수금과 성공보수가 동시에 미결제일 수는 없어 단건으로 충분하다.
     */
    Optional<Settlement> findPayableByProjectId(Long projectId);
}
