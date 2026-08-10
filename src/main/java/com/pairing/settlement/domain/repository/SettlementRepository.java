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

    /**
     * 아직 내지 않은 정산이 있는지. 탈퇴 가능 여부 판정용이라 건수를 세지 않고 존재만 본다.
     *
     * <p>기준은 {@link #findPayableByProjectId} 와 같다. 결제할 수 있는 상태 = 아직 안 낸 상태다.
     */
    boolean existsUnpaidByPayer(Long payerAccountId);
}
