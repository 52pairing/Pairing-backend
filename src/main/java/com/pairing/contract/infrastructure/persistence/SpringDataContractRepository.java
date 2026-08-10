package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 계약 JPA 리포지토리.
 *
 * <p>서명 컬렉션은 JOIN FETCH 하지 않는다. 페이징이 인메모리로 떨어진다.
 * 엔티티의 {@code @BatchSize} 가 N+1 을 막는다.
 *
 * <p>내 계약 목록은 {@code contract_signature.account_id} 로 찾는다. 계약의
 * {@code client_id}/{@code freelancer_id} 는 프로필 ID 라 로그인 계정으로 바로 못 건다.
 */
public interface SpringDataContractRepository extends JpaRepository<ContractJpaEntity, Long> {

    Optional<ContractJpaEntity> findByNegotiationId(Long negotiationId);

    List<ContractJpaEntity> findByProjectIdOrderByIdAsc(Long projectId);

    long countByPositionIdAndStatusIn(Long positionId, List<ContractStatus> statuses);

    /** status 가 null 이면 그 조건을 건너뛴다. */
    @Query("""
            SELECT c FROM ContractJpaEntity c
             WHERE EXISTS (SELECT 1 FROM ContractSignatureJpaEntity s
                            WHERE s.contract = c AND s.accountId = :accountId)
               AND (:status IS NULL OR c.status = :status)
            """)
    Page<ContractJpaEntity> findByParty(@Param("accountId") Long accountId,
                                        @Param("status") ContractStatus status,
                                        Pageable pageable);
}
