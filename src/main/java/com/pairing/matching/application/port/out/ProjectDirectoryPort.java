package com.pairing.matching.application.port.out;

import com.pairing.matching.application.result.ProjectContent;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.project.domain.model.ProjectStatus;

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

    /**
     * 클라이언트 회사 프로필 요약(업종·직원수). account 도메인 값이라 매칭 시점에 고정할 필요가
     * 없어(R32는 project 도메인의 수정 가능 필드에만 적용) 항상 라이브로 읽는다.
     */
    String findCompanyProfile(Long projectId);

    /** 포지션의 모집 인원. 노출 수·인원 초과 검증에 쓴다. */
    int findHeadcount(Long positionId);

    /**
     * 프로젝트 대표 상태. 매칭 요청 발송·재추천 전에 모집 종료·취소된 프로젝트를 걸러내는 데 쓴다
     * (모집 종료로 강제 마감된 포지션은 인원이 안 찼어도 CLOSED라 인원 초과 검증만으로는 못 막는다).
     * 프로젝트가 없으면 {@code PJ_001}을 던진다.
     */
    ProjectStatus findStatus(Long projectId);

    /** 프로젝트에 속한 포지션 ID 전체(positionNo 오름차순). 결제 완료 후 포지션별로 초기 라운드를 돌릴 때 쓴다. */
    List<Long> findPositionIds(Long projectId);

    /**
     * 매칭 요청 카드 노출용 프로젝트·포지션 요약.
     *
     * <p>projectId를 매칭이 직접 넘긴다 — project 도메인은 positionId만으로 projectId를
     * 역조회하는 방법을 제공하지 않고, 매칭은 자신의 MatchingRound/MatchingRequest에
     * 이미 그 매핑을 갖고 있어 넘기는 쪽이 더 자연스럽다.
     */
    ProjectPositionSummary findPositionSummary(Long projectId, Long positionId);

    /**
     * projectId를 모를 때 쓰는 조회. 반환값의 {@code projectId}로 소유자를 검증한다.
     *
     * <p><b>추천 라운드가 아직 없을 때 필요하다.</b> 후보 조회는 보통 라운드에서 projectId를 얻는데,
     * 결제 직후에는 라운드를 만드는 비동기 작업이 아직 안 끝나 라운드가 없다. 그 상태에서도 소유자
     * 확인은 해야 하므로 포지션에서 프로젝트를 거슬러 올라간다.
     */
    ProjectPositionSummary findPositionSummary(Long positionId);

    /**
     * 프리랜서가 수락 전에 보는 프로젝트 본문(진행 상황·세부 업무 범위·우대사항·근무 장소 등).
     *
     * <p>요약본({@link #findPositionSummary})과 나눈 이유는 {@link ProjectContent} 주석 참고 —
     * 요약본은 재색인 루프까지 쓰는 경로라 화면용 필드 때문에 조회를 늘리지 않는다.
     */
    ProjectContent findProjectContent(Long projectId);
}
