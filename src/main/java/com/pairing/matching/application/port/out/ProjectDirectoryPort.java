package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.ProjectPositionSummary;

import java.util.List;

/**
 * project 도메인 조회 포트. 매칭은 이 인터페이스로만 프로젝트·포지션 정보를 읽는다.
 *
 * <p><b>임시 스텁 상태(2026-08-07)</b>: project 도메인에 아직 실제 영속 계층이 없어
 * {@code infrastructure.directory.StubProjectDirectoryAdapter}가 고정값을 돌려준다.
 * project 도메인이 실제 구현되면 이 어댑터 하나만 실제 조회 코드로 교체하면 된다.
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

    /** 매칭 요청 카드 노출용 프로젝트·포지션 요약. */
    ProjectPositionSummary findPositionSummary(Long positionId);
}
