package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.ProjectPositionSummary;

import java.util.List;

/**
 * project 도메인 조회 포트. 매칭은 이 인터페이스로만 프로젝트·포지션 정보를 읽는다.
 *
 * <p>{@code infrastructure.directory.ProjectDirectoryAdapter}가 project 도메인의
 * {@code ProjectQueryUseCase}와 account 도메인의 {@code AccountQueryUseCase}를 조합해 구현한다.
 */
public interface ProjectDirectoryPort {

    /** 이 프로젝트가 해당 계정(클라이언트) 소유인지. 보낸 요청 조회의 접근 제어에 쓴다. */
    boolean isOwnedByAccount(Long projectId, Long accountId);

    /** 계정이 소유한 프로젝트 ID 전체. projectId 파라미터 없이 "내가 보낸 요청 전체"를 조회할 때 쓴다. */
    List<Long> findProjectIdsOwnedByAccount(Long accountId);

    /** 프로젝트를 등록한 클라이언트의 accountId. budgetCap 계산 시 등급 조회에 쓴다. */
    Long findClientAccountId(Long projectId);

    /** 포지션의 모집 인원. 노출 수·인원 초과 검증에 쓴다. */
    int findHeadcount(Long positionId);

    /**
     * 매칭 요청 카드 노출용 프로젝트·포지션 요약.
     *
     * <p>projectId를 매칭이 직접 넘긴다 — project 도메인은 positionId만으로 projectId를
     * 역조회하는 방법을 제공하지 않고, 매칭은 자신의 MatchingRound/MatchingRequest에
     * 이미 그 매핑을 갖고 있어 넘기는 쪽이 더 자연스럽다.
     */
    ProjectPositionSummary findPositionSummary(Long projectId, Long positionId);
}
