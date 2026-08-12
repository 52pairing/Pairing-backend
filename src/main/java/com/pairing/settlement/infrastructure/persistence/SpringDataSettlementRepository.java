package com.pairing.settlement.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpringDataSettlementRepository extends JpaRepository<SettlementJpaEntity, Long> {

    /**
     * projectId / phase / status 가 null 이면 그 조건은 건너뛴다.
     *
     * <p>최신순으로 고정한다. 정렬이 없으면 페이지를 넘길 때 순서가 달라져 같은 정산이 두 번
     * 보이거나 빠진다. 생성 시각은 같은 초에 겹칠 수 있어 id 로 매긴다.
     */
    @Query("""
            SELECT s FROM SettlementJpaEntity s
             WHERE s.payerAccountId = :payerAccountId
               AND (:projectId IS NULL OR s.projectId = :projectId)
               AND (:phase IS NULL OR s.phase = :phase)
               AND (:status IS NULL OR s.status = :status)
             ORDER BY s.id DESC
            """)
    Page<SettlementJpaEntity> findByPayer(@Param("payerAccountId") Long payerAccountId,
                                          @Param("projectId") Long projectId,
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

    /** 계약 1건에 착수금·성공보수가 각각 붙는다. phase 를 함께 걸어야 단건이 된다. */
    Optional<SettlementJpaEntity> findByContractIdAndPhase(Long contractId, SettlementPhase phase);

    /**
     * 프리랜서 착수금을 이미 낸 계약 ID 들.
     *
     * <p>계약 목록 화면이 계약마다 "결제 필요" 배지를 띄울지 판단하는 데 쓴다. 한 건씩 물으면
     * 페이지 크기만큼 쿼리가 늘어나서 목록의 계약 ID 를 한 번에 넣고 받는다.
     *
     * <p>빈 목록을 넘기면 {@code IN ()} 이 되어 DB 에 따라 문법 오류가 난다. 호출부가 막는다.
     */
    @Query("""
            SELECT s.contractId FROM SettlementJpaEntity s
             WHERE s.contractId IN :contractIds
               AND s.payerRole = com.pairing.meta.domain.model.PartyRole.FREELANCER
               AND s.phase = com.pairing.settlement.domain.model.SettlementPhase.DEPOSIT
               AND s.status = com.pairing.settlement.domain.model.SettlementStatus.PAID
            """)
    List<Long> findPaidFreelancerDepositContractIds(@Param("contractIds") Collection<Long> contractIds);

    /**
     * 계약별로 이 사람이 지금 결제할 정산.
     *
     * <p>계약 목록의 결제 버튼이 쓴다. 계약 1건에 착수금·성공보수가 붙는데 둘이 동시에 미결제일
     * 일은 없다. 결제 실패로 여러 건이 남는 경우를 대비해 <b>오래된 것부터</b> 준다.
     *
     * <p>{@code payerAccountId} 로 좁히므로 클라이언트가 불러도 남의 정산이 나오지 않는다.
     * 계약에 걸린 정산은 전부 프리랜서 몫이라 클라이언트에게는 빈 결과가 돌아간다.
     */
    @Query("""
            SELECT s.contractId, s.id FROM SettlementJpaEntity s
             WHERE s.contractId IN :contractIds
               AND s.payerAccountId = :payerAccountId
               AND s.status IN :statuses
             ORDER BY s.id ASC
            """)
    List<Object[]> findPayableByContractIds(@Param("payerAccountId") Long payerAccountId,
                                            @Param("contractIds") Collection<Long> contractIds,
                                            @Param("statuses") List<SettlementStatus> statuses);

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
