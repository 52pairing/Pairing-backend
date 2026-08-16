package com.pairing.contract.domain.repository;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.ContractTab;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 계약 애그리거트 리포지토리 포트.
 *
 * <p>{@code clientId} 는 {@code client_profile.id}, {@code freelancerId} 는
 * {@code freelancer_profile.id} 다. {@code account.id} 가 아니다. 변환은 application 계층이 한다.
 */
public interface ContractRepository {

    /** 서명 2건까지 함께 저장한다. 생성 전용이다. */
    Contract save(Contract contract);

    /**
     * 상태 전이 저장. 서명 상태까지 반영하되 행을 재생성하지 않는다.
     *
     * <p>{@link #save} 는 생성용이다. 이미 저장된 계약에 그걸 쓰면 서명이 다시 INSERT 되어
     * {@code uk_contract_signature} 에 걸린다. 서명·체결·파기 경로는 이 메서드를 쓴다.
     */
    Contract updateState(Contract contract);

    /** 상세 조회. 서명을 함께 로드한다. */
    Optional<Contract> findById(Long contractId);

    /** 협상 1건당 계약 1건이다. 중복 생성 방지에 쓴다. */
    Optional<Contract> findByNegotiationId(Long negotiationId);

    /** 탭을 쓰지 않는 조회. 전체 목록이 필요한 호출부가 쓴다. */
    default Page<Contract> findByParty(Long accountId, Long projectId, ContractStatus status,
                                       Pageable pageable) {
        return findByParty(accountId, projectId, status, null, pageable);
    }

    /**
     * 내 계약 목록. projectId / status 가 null 이면 그 조건을 걸지 않는다.
     *
     * <p>{@code projectId} 는 프로젝트 상세의 계약 탭용이다. 헤더의 "내 계약" 은 null 로 부른다.
     *
     * <p>{@code tab} 은 계약관리 화면의 탭이다. 내 서명 상태와 계약 상태 묶음으로 풀린다.
     * null 이면 {@link ContractTab#ALL} 과 같다.
     */
    Page<Contract> findByParty(Long accountId, Long projectId, ContractStatus status,
                               ContractTab tab, Pageable pageable);

    /**
     * 탭별 건수. 탭 옆 배지에 쓴다.
     *
     * <p>{@code projectId} 가 null 이면 내 계약 전체다. 값을 주면 그 프로젝트의 내 계약만 센다
     * ({@link #findByParty} 와 같은 규칙).
     *
     * <p><b>건수가 0인 탭도 키로 포함한다.</b> 화면이 탭을 전부 그려야 하기 때문이다.
     *
     * <p>{@link ContractTab} 7개를 모두 담는다. 클라이언트 화면과 프리랜서 화면이 그중 다른
     * 5개씩을 나눠 쓰므로, 어느 쪽을 부르는지 서버가 묻지 않고 전부 내려준 뒤 화면이 고른다.
     * 클라·프리 양쪽인 계정이 있어 역할로는 가릴 수 없다.
     */
    Map<ContractTab, Long> countMyTabs(Long accountId, Long projectId);

    /** 프로젝트에 걸린 계약 전체. 진행중 전환 판정과 프로젝트 상세에 쓴다. */
    List<Contract> findByProjectId(Long projectId);

    /** 포지션의 체결 완료 건수. 인원 충족 판정에 쓴다. */
    long countSignedByPositionId(Long positionId);

    /**
     * DRAFT 로 멈춘 계약의 id. 문구 채우기가 실패해 서명 단계로 못 넘어간 것들이다.
     *
     * <p>엔티티가 아니라 id 만 준다. 채우는 쪽이 자기 트랜잭션에서 다시 읽어야 하기 때문이다 —
     * 조회와 처리 사이에 이미 채워졌을 수 있고, 엔티티를 넘기면 다른 트랜잭션의 준영속
     * 인스턴스를 들고 다니게 된다.
     *
     * <p>오래된 것부터 준다. 포기 시한이 가까운 계약이 먼저 결론 나야 한다.
     *
     * @param stuckBefore 이 시각 이전에 만들어진 것만. 아직 응답을 기다리는 중인 계약을 다시
     *                    집으면 같은 계약에 AI 호출이 두 번 나간다
     * @param limit       한 번에 집을 최대 건수
     */
    List<Long> findStuckDraftIds(LocalDateTime stuckBefore, int limit);
}
