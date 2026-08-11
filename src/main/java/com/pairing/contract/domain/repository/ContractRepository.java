package com.pairing.contract.domain.repository;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    /**
     * 내 계약 목록. projectId / status 가 null 이면 그 조건을 걸지 않는다.
     *
     * <p>{@code projectId} 는 프로젝트 상세의 계약 탭용이다. 헤더의 "내 계약" 은 null 로 부른다.
     */
    Page<Contract> findByParty(Long accountId, Long projectId, ContractStatus status, Pageable pageable);

    /** 프로젝트에 걸린 계약 전체. 진행중 전환 판정과 프로젝트 상세에 쓴다. */
    List<Contract> findByProjectId(Long projectId);

    /** 포지션의 체결 완료 건수. 인원 충족 판정에 쓴다. */
    long countSignedByPositionId(Long positionId);
}
