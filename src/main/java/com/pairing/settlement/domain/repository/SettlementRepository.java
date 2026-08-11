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

    /** 계약에 걸린 정산. 프리랜서 착수금은 계약 1건당 1건이라 중복 생성을 여기서 막는다. */
    Optional<Settlement> findByContractId(Long contractId);

    /** 내 정산 목록. phase / status 는 null 이면 필터하지 않는다. */
    Page<Settlement> findByPayer(Long payerAccountId, SettlementPhase phase,
                                 SettlementStatus status, Pageable pageable);

    /**
     * 프로젝트에 걸린 <b>클라이언트</b>의 결제 대기 정산 1건.
     *
     * <p>화면의 결제 버튼이 쓴다. 계약이 체결되면 같은 프로젝트에 프리랜서 착수금이 여러 건 붙으므로
     * 역할로 좁힌다. 클라이언트 기준으로는 착수금과 성공보수가 동시에 미결제일 수 없어 단건이면 된다.
     */
    Optional<Settlement> findPayableByProjectId(Long projectId);

    /**
     * 아직 내지 않은 정산이 있는지. 탈퇴 가능 여부 판정용이라 건수를 세지 않고 존재만 본다.
     *
     * <p>기준은 {@link #findPayableByProjectId} 와 같다. 결제할 수 있는 상태 = 아직 안 낸 상태다.
     */
    boolean existsUnpaidByPayer(Long payerAccountId);

    /**
     * 아직 안 낸 프리랜서 착수금이 남아 있는지.
     *
     * <p>전원이 계약하고 착수금까지 냈을 때 프로젝트를 진행중으로 넘기는 판정에 쓴다.
     * 건수가 아니라 존재만 보면 된다.
     */
    boolean existsUnpaidFreelancerDeposit(Long projectId);
}
