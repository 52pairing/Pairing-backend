package com.pairing.settlement.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataSettlementRepository extends JpaRepository<SettlementJpaEntity, Long> {

    /** phase / status 가 null 이면 그 조건은 건너뛴다. */
    @Query("""
            SELECT s FROM SettlementJpaEntity s
             WHERE s.payerAccountId = :payerAccountId
               AND (:phase IS NULL OR s.phase = :phase)
               AND (:status IS NULL OR s.status = :status)
            """)
    Page<SettlementJpaEntity> findByPayer(@Param("payerAccountId") Long payerAccountId,
                                          @Param("phase") SettlementPhase phase,
                                          @Param("status") SettlementStatus status,
                                          Pageable pageable);

    /**
     * 프로젝트에서 한 당사자가 낼 결제 대기 정산. 오래된 것부터 준다.
     *
     * <p>{@code payerRole} 로 좁히는 이유는, 계약이 체결되면 같은 프로젝트에 프리랜서 착수금이
     * 여러 건 붙기 때문이다. 역할을 안 걸면 클라이언트 결제 버튼이 남의 정산을 물어 온다.
     *
     * <p>한 당사자 기준으로는 착수금과 성공보수가 동시에 미결제일 수 없지만, 결제 실패로 여러 건이
     * 남는 경우를 대비해 정렬을 고정한다.
     */
    List<SettlementJpaEntity> findByProjectIdAndPayerRoleAndStatusInOrderByIdAsc(
            Long projectId, PartyRole payerRole, List<SettlementStatus> statuses);

    /** 계약에 걸린 정산. 프리랜서 착수금은 계약 1건당 1건이다. */
    Optional<SettlementJpaEntity> findByContractId(Long contractId);

    /** 탈퇴 가능 여부 판정용. 행을 읽지 않고 존재만 확인한다. */
    boolean existsByPayerAccountIdAndStatusIn(Long payerAccountId, List<SettlementStatus> statuses);

    /** 프로젝트를 진행중으로 넘길 수 있는지 판정용. 아직 안 낸 프리랜서 착수금이 있는지만 본다. */
    boolean existsByProjectIdAndPayerRoleAndPhaseAndStatusIn(
            Long projectId, PartyRole payerRole, SettlementPhase phase, List<SettlementStatus> statuses);

    default Optional<SettlementJpaEntity> findFirstPayable(Long projectId, PartyRole payerRole,
                                                           List<SettlementStatus> statuses) {
        return findByProjectIdAndPayerRoleAndStatusInOrderByIdAsc(projectId, payerRole, statuses)
                .stream().findFirst();
    }
}
