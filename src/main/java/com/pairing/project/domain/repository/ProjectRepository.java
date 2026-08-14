package com.pairing.project.domain.repository;

import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
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

    /**
     * 상태 전이 저장. 포지션·첨부는 건드리지 않는다.
     *
     * <p>{@link #save} 는 등록용이다. 이미 저장된 프로젝트에 그걸 쓰면 자식이 재삽입되어
     * {@code uk_position_skill} 에 걸린다. 상태만 바뀌는 경로는 이 메서드를 쓴다.
     */
    Project updateState(Project project);

    /**
     * 상태 전이 저장 + 포지션 상태. 포지션 구성과 첨부는 건드리지 않는다.
     *
     * <p>모집 종료·만료·중도 종료·완료처럼 프로젝트와 포지션을 함께 닫는 경로가 쓴다.
     * {@link #updateState} 는 프로젝트 스칼라만 옮겨서 포지션이 열린 채로 남고,
     * {@link #updateDetail} 은 수정용이라 상태를 아예 옮기지 않는다.
     *
     * <p>포지션을 추가·삭제하지 않으므로 유니크 제약을 피하는 2단계 동기화가 필요 없다.
     */
    Project updateStateWithPositions(Project project);

    /**
     * 등록 정보 수정 저장. 포지션·첨부까지 반영한다.
     *
     * <p>포지션은 id 를 유지한 채 필드만 갱신한다. 매칭·협상·계약이 {@code project_position.id} 를
     * 참조하므로 삭제 후 재생성하면 그 연결이 끊긴다.
     */
    Project updateDetail(Project project);

    /** 상세 조회. 포지션과 요구 스킬을 함께 로드한다. */
    Optional<Project> findById(Long projectId);

    /**
     * 모집 마감이 지났는데 인원이 확정되지 않은 프로젝트. (정책 P46 만료 처리)
     *
     * <p>연장 여부는 보지 않는다. 연장하지 않아도 기본 2주가 지나면 만료 대상이다.
     *
     * <p>모집중뿐 아니라 <b>협상중·계약 대기</b>도 대상이다. 수락 한 건에 상태가 넘어가므로
     * 상태로 거르면 대부분의 프로젝트가 만료 대상에서 빠진다. 판정 기준은 인원이다.
     */
    List<Project> findExpiredUnderstaffed(LocalDateTime now);

    /**
     * 소유 확인과 등급 조회에 쓰는 경량 조회.
     * 애그리거트 전체를 로드하지 않으려고 client_id 만 뽑는다.
     */
    Optional<Long> findClientIdById(Long projectId);

    /** 상태 검사용 경량 조회. 애그리거트를 로드하지 않는다. */
    Optional<ProjectStatus> findStatusById(Long projectId);

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

    /** 포지션이 속한 프로젝트 ID. Position 이 projectId 를 들고 있지 않아 따로 조회한다. */
    Optional<Long> findProjectIdByPositionId(Long positionId);
}