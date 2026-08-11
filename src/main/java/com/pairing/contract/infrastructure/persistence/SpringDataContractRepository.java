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

    /**
     * projectId / status 가 null 이면 그 조건을 건너뛴다.
     *
     * <p>{@code projectId} 는 프로젝트 상세의 계약 탭이 쓴다. 이 파라미터가 없으면 그 화면이
     * 헤더의 "내 계약" 과 같은 목록을 받아 다른 프로젝트 계약까지 섞여 나온다.
     *
     * <p>최신순으로 고정한다. 정렬이 없으면 DB 가 임의 순서로 돌려주는데, 페이지를 넘길 때
     * 순서가 달라지면 같은 계약이 두 번 보이거나 빠진다. 방금 타결된 계약이 첫 화면에
     * 보이지 않는 것도 같은 이유다.
     *
     * <p>{@code created_at} 이 아니라 id 로 정렬한다. 생성 시각은 DB 기본값이라 같은 초에
     * 만들어진 계약끼리 값이 겹칠 수 있고, 겹치면 그 안의 순서가 다시 불안정해진다.
     * id 는 단조 증가하고 중복이 없어 페이지 경계가 흔들리지 않는다.
     */
    @Query("""
            SELECT c FROM ContractJpaEntity c
             WHERE EXISTS (SELECT 1 FROM ContractSignatureJpaEntity s
                            WHERE s.contract = c AND s.accountId = :accountId)
               AND (:projectId IS NULL OR c.projectId = :projectId)
               AND (:status IS NULL OR c.status = :status)
             ORDER BY c.id DESC
            """)
    Page<ContractJpaEntity> findByParty(@Param("accountId") Long accountId,
                                        @Param("projectId") Long projectId,
                                        @Param("status") ContractStatus status,
                                        Pageable pageable);
}
