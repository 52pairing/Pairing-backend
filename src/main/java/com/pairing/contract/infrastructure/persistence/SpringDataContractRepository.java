package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
     * projectId / status / mySignatureStatus 가 null 이면 그 조건을 건너뛴다.
     *
     * <p>{@code projectId} 는 프로젝트 상세의 계약 탭이 쓴다. 이 파라미터가 없으면 그 화면이
     * 헤더의 "내 계약" 과 같은 목록을 받아 다른 프로젝트 계약까지 섞여 나온다.
     *
     * <p>{@code mySignatureStatus} 와 {@code tabStatuses} 는 {@code ContractTab} 이 푼 값이다.
     * "서명 대기"와 "상대방 서명 대기"는 계약 상태가 둘 다 {@code SIGN_PENDING} 이라
     * 계약 상태만으로는 못 가른다. <b>내 서명 행</b>의 상태를 EXISTS 안에서 함께 걸어야 갈린다.
     *
     * <p>{@code tabStatuses} 는 null 을 받지 않는다. {@code IN ()} 이 되면 DB 가 거부하므로
     * 조건을 걸지 않는 탭도 전체 상태를 담아 넘긴다.
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
                            WHERE s.contract = c AND s.accountId = :accountId
                              AND (:mySignatureStatus IS NULL OR s.status = :mySignatureStatus))
               AND (:projectId IS NULL OR c.projectId = :projectId)
               AND (:status IS NULL OR c.status = :status)
               AND c.status IN :tabStatuses
             ORDER BY c.id DESC
            """)
    Page<ContractJpaEntity> findByParty(@Param("accountId") Long accountId,
                                        @Param("projectId") Long projectId,
                                        @Param("status") ContractStatus status,
                                        @Param("mySignatureStatus") SignatureStatus mySignatureStatus,
                                        @Param("tabStatuses") List<ContractStatus> tabStatuses,
                                        Pageable pageable);

    /**
     * 탭 배지용 집계. (계약 상태 x 내 서명 상태) 조합별 건수를 <b>한 번에</b> 돌려준다.
     *
     * <p>탭마다 세면 7번 나간다. 두 축으로 묶어 한 번 받고 탭으로 접는 일은 어댑터가 한다.
     * 접는 규칙이 {@code ContractTab} 한 곳에만 있어 목록과 배지가 어긋날 수 없다.
     *
     * <p>{@code findByParty} 는 {@code EXISTS} 를 쓰는데 여기는 {@code JOIN} 이다.
     * 서명 행의 상태를 <b>결과로 꺼내야</b> 하기 때문이다. 계약 1건에 서명은 갑·을 2행뿐이고
     * {@code accountId} 로 걸어 그중 1행만 남으므로 건수가 부풀지 않는다
     * ({@code uk_contract_signature} 가 (계약, 당사자) 중복을 막는다).
     */
    @Query("""
            SELECT new com.pairing.contract.infrastructure.persistence.ContractStatusCountRow(
                       c.status, s.status, COUNT(c))
              FROM ContractJpaEntity c
              JOIN ContractSignatureJpaEntity s ON s.contract = c AND s.accountId = :accountId
             WHERE (:projectId IS NULL OR c.projectId = :projectId)
             GROUP BY c.status, s.status
            """)
    List<ContractStatusCountRow> countByPartyGroupedByStatus(@Param("accountId") Long accountId,
                                                             @Param("projectId") Long projectId);

    /**
     * DRAFT 로 멈춘 계약의 id. 문구 채우기 복구 배치가 쓴다.
     *
     * <p>{@code createdAt} 으로 거른다. 방금 만들어진 계약은 지금 이 순간 AI 응답을 기다리는
     * 중일 수 있고, 그걸 집으면 같은 계약에 호출이 두 번 나가 Gemini 비용이 두 배가 된다.
     *
     * <p>DRAFT 는 평상시 0건에 가까운 과도기 상태라 별도 인덱스를 두지 않았다. 계약이 쌓여
     * 이 조회가 느려지면 {@code (created_at) WHERE status = 'DRAFT'} 부분 인덱스를 만든다.
     */
    @Query("""
            SELECT c.id FROM ContractJpaEntity c
             WHERE c.status = com.pairing.contract.domain.model.ContractStatus.DRAFT
               AND c.createdAt < :stuckBefore
             ORDER BY c.id ASC
            """)
    List<Long> findStuckDraftIds(@Param("stuckBefore") LocalDateTime stuckBefore, Pageable pageable);
}
