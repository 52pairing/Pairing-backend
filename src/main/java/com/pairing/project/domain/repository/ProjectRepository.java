package com.pairing.project.domain.repository;

import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 프로젝트 애그리거트 리포지토리 포트.
 *
 * <p>모든 조회는 삭제된 프로젝트({@code deleted_at IS NOT NULL})를 제외한다.
 * clientId 인자는 전부 {@code client_profile.id} 다. {@code account.id} 가 아니다.
 */
public interface ProjectRepository {

    /** 포지션·요구 스킬·첨부까지 함께 저장한다. */
    Project save(Project project);

    /** 상세 조회. 포지션과 요구 스킬을 함께 로드한다. */
    Optional<Project> findById(Long projectId);

    /**
     * 소유 확인과 등급 조회에 쓰는 경량 조회.
     * 애그리거트 전체를 로드하지 않으려고 client_id 만 뽑는다.
     */
    Optional<Long> findClientIdById(Long projectId);

    /** 계정이 소유한 프로젝트 ID 전체. 상태와 무관하다(취소·종료 포함). 없으면 빈 리스트. */
    List<Long> findIdsByClientId(Long clientProfileId);

    /** 내 프로젝트 목록. statuses 가 비어 있으면 전체 상태를 조회한다. */
    Page<Project> findByClientId(Long clientProfileId, List<ProjectStatus> statuses, Pageable pageable);

    /** 탭 배지용 상태별 건수. 건수가 0인 상태는 결과에 포함되지 않는다. */
    Map<ProjectStatus, Long> countByStatus(Long clientProfileId);

    /** 관리자 목록. status 와 keyword 는 null 이면 조건에서 제외한다. */
    Page<Project> findAllForAdmin(ProjectStatus status, String keyword, Pageable pageable);

    /** 포지션 단건. 매칭이 projectId 없이 모집 인원만 조회할 때 쓴다. */
    Optional<Position> findPositionById(Long positionId);
}