package com.pairing.settlement.infrastructure.persistence;

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
     * 프로젝트의 결제 대기 정산. 오래된 것부터 준다.
     *
     * <p>착수금과 성공보수가 동시에 미결제일 수는 없지만, 결제 실패로 여러 건이 남는 경우를 대비해
     * 정렬을 고정한다.
     */
    List<SettlementJpaEntity> findByProjectIdAndStatusInOrderByIdAsc(
            Long projectId, List<SettlementStatus> statuses);

    /** 탈퇴 가능 여부 판정용. 행을 읽지 않고 존재만 확인한다. */
    boolean existsByPayerAccountIdAndStatusIn(Long payerAccountId, List<SettlementStatus> statuses);

    default Optional<SettlementJpaEntity> findFirstPayable(Long projectId, List<SettlementStatus> statuses) {
        return findByProjectIdAndStatusInOrderByIdAsc(projectId, statuses).stream().findFirst();
    }
}
