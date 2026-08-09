package com.pairing.project.application.usecase;

import com.pairing.project.application.result.ProjectDetail;
import com.pairing.project.application.result.ProjectPositionSummary;
import com.pairing.project.application.result.ProjectSummary;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.model.ProjectTab;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * 프로젝트 조회 인바운드 포트. 매칭·협상·계약 도메인이 이 인터페이스만 호출한다.
 *
 * <p><b>accountId 와 clientProfileId 는 다른 값이다.</b> {@code project.client_id} 는
 * {@code client_profile.id} 를 가리킨다. 변환은 이 포트 안에서 처리하므로
 * 호출부는 {@code @CurrentAccountId} 값을 그대로 넘기면 된다.
 *
 * <p>대상이 없으면 {@code BusinessException} 을 던진다. null 을 반환하지 않는다.
 */
public interface ProjectQueryUseCase {

    /** 접근 제어용. 프로젝트가 없으면 false. */
    boolean isOwnedBy(Long projectId, Long accountId);

    /** 상태와 무관하게 전부 내려간다(취소·종료 포함). 없으면 빈 리스트. */
    List<Long> findProjectIdsByAccountId(Long accountId);

    /** 등급·회사명 조회 키. account 도메인의 findClientProfileById 에 그대로 넣는다. */
    Long findClientProfileId(Long projectId);

    int findHeadcount(Long positionId);

    /** 취소·모집 종료된 프로젝트에 매칭이 계속 도는 것을 막을 때 쓴다. 없으면 PJ_001. */
    ProjectStatus findStatus(Long projectId);

    ProjectPositionSummary findProjectPositionSummary(Long projectId, Long positionId);

    /** projectId 를 모르는 호출자용. 포지션이 없으면 PJ_002. */
    ProjectPositionSummary findProjectPositionSummary(Long positionId);

    /** 프로젝트의 모든 포지션 요약. positionNo 오름차순. 결제 후 포지션별로 추천을 돌릴 때 쓴다. */
    List<ProjectPositionSummary> findPositionSummaries(Long projectId);

    /** 상세 조회. 없으면 PJ_001. */
    Project getById(Long projectId);

    /** 상세 조회 + 열람 권한 확인. 없으면 PJ_001, 소유자가 아니면 PJ_003. */
    Project getByIdForOwner(Long projectId, Long accountId);

    /** 상세 화면용. 첨부 메타까지 합쳐 준다. */
    ProjectDetail getDetail(Long projectId);

    /** 상세 화면용. 소유자가 아니면 PJ_003. */
    ProjectDetail getDetailForOwner(Long projectId, Long accountId);

    /**
     * 내 프로젝트 목록. 탭 하나가 여러 상태를 묶는다.
     *
     * <p>{@code tab} 이 null 이면 상태 필터 없이 전부 내려간다.
     */
    Page<ProjectSummary> findMine(Long accountId, ProjectTab tab, Pageable pageable);

    /**
     * 탭별 건수. 탭 옆 배지에 쓴다.
     *
     * <p>건수가 0인 탭도 키로 포함한다. 화면이 탭을 전부 그려야 하기 때문이다.
     */
    Map<ProjectTab, Long> countMyTabs(Long accountId);

}