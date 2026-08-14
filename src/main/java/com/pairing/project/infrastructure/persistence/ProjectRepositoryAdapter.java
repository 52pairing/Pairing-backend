package com.pairing.project.infrastructure.persistence;

import com.pairing.global.exception.BusinessException;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.project.exception.ProjectErrorCode;
import com.pairing.project.infrastructure.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프로젝트 리포지토리 어댑터.
 *
 * <p>포지션 저장은 Project 애그리거트의 cascade 로 처리한다. 포지션 리포지토리는
 * 매칭이 projectId 없이 포지션만 조회할 때만 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {

    /** 번호를 맞바꿀 때 잠시 피해 둘 자리. 포지션은 최대 100건이라 여기와 겹치지 않는다. */
    private static final int TEMP_POSITION_NO_BASE = 1_000;

    private final SpringDataProjectRepository springDataRepository;
    private final SpringDataProjectPositionRepository positionRepository;
    private final ProjectMapper projectMapper;

    @Override
    public Project save(Project project) {
        ProjectJpaEntity saved = springDataRepository.save(projectMapper.toJpaEntity(project));
        return projectMapper.toDomain(saved);
    }

    @Override
    public Project updateState(Project project) {
        ProjectJpaEntity entity = springDataRepository.findByIdAndDeletedAtIsNull(project.getId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 영속 엔티티를 그대로 두고 스칼라만 갱신한다. 변경 감지가 커밋 시점에 UPDATE 를 만든다.
        projectMapper.applyState(entity, project);
        return projectMapper.toDomain(entity);
    }

    @Override
    public Project updateStateWithPositions(Project project) {
        ProjectJpaEntity entity = springDataRepository.findByIdAndDeletedAtIsNull(project.getId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        projectMapper.applyState(entity, project);

        Map<Long, ProjectPositionJpaEntity> byId = entity.getPositions().stream()
                .collect(Collectors.toMap(ProjectPositionJpaEntity::getId, Function.identity()));

        // 구성은 그대로 두고 확정 인원과 마감 결과만 옮긴다. 번호도 스킬도 바뀌지 않아
        // 유니크 제약과 무관하다.
        for (Position position : project.getPositions()) {
            ProjectPositionJpaEntity target = byId.get(position.getId());
            if (target != null) {
                target.applyState(position.getConfirmedCount(),
                        position.getStatus(), position.getClosedAt());
            }
        }
        return projectMapper.toDomain(entity);
    }

    @Override
    public Project updateDetail(Project project) {
        ProjectJpaEntity entity = springDataRepository.findByIdAndDeletedAtIsNull(project.getId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        projectMapper.applyEditable(entity, project);

        Set<Long> keepIds = project.getPositions().stream()
                .map(Position::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        entity.removePositionsNotIn(keepIds);

        Map<Long, ProjectPositionJpaEntity> survivors = entity.getPositions().stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(ProjectPositionJpaEntity::getId, Function.identity()));

        // 1단계. 지울 것과 비울 것을 먼저 내보낸다.
        //   position_no 는 (project_id, position_no) UNIQUE 라 자리를 맞바꾸면 중간 상태가 충돌한다.
        //   살아남은 포지션을 겹치지 않는 번호로 옮겨두고, 스킬도 비운 뒤 flush 해서 DELETE 를 먼저 태운다.
        int tempNo = TEMP_POSITION_NO_BASE;
        for (ProjectPositionJpaEntity survivor : survivors.values()) {
            survivor.applyCondition(tempNo++, survivor.getJobCategory(), survivor.getJobRole(),
                    survivor.getMinCareerYears(), survivor.getHeadcount());
            survivor.clearSkills();
        }
        entity.clearFiles();
        springDataRepository.flush();

        // 2단계. 최종 값을 채운다. 여기서부터는 충돌할 상대가 없다.
        for (Position position : project.getPositions()) {
            if (position.getId() == null) {
                entity.addPosition(projectMapper.toPositionJpaEntity(position));
                continue;
            }
            ProjectPositionJpaEntity target = survivors.get(position.getId());
            target.applyCondition(position.getPositionNo(), position.getJobCategory(),
                    position.getJobRole(), position.getMinCareerYears(), position.getHeadcount());

            for (SkillCode skill : position.getSkills()) {
                target.addSkill(new PositionSkillJpaEntity(null, skill));
            }
        }

        List<Long> fileIds = project.getFileIds();
        for (int i = 0; i < fileIds.size(); i++) {
            entity.addFile(new ProjectFileJpaEntity(null, fileIds.get(i), i));
        }
        springDataRepository.flush();

        return projectMapper.toDomain(entity);
    }

    @Override
    public Optional<Project> findById(Long projectId) {
        return springDataRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(projectMapper::toDomain);
    }

    /**
     * 만료 대상 상태. 진행중 이후는 인원이 차야 도달하므로 인원 조건만으로도 걸러지지만,
     * 파생값({@code confirmed_headcount})이 어긋났을 때 진행 중인 프로젝트가 끌려오지 않도록
     * 상태로 한 번 더 막는다.
     */
    private static final List<ProjectStatus> CANCELABLE_STATUSES = List.of(
            ProjectStatus.RECRUITING, ProjectStatus.NEGOTIATING, ProjectStatus.CONTRACT_PENDING);

    @Override
    public List<Project> findExpiredUnderstaffed(LocalDateTime now) {
        return springDataRepository.findExpiredUnderstaffed(CANCELABLE_STATUSES, now).stream()
                .map(projectMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Long> findClientIdById(Long projectId) {
        return springDataRepository.findClientIdById(projectId);
    }

    @Override
    public Optional<ProjectStatus> findStatusById(Long projectId) {
        return springDataRepository.findStatusById(projectId);
    }

    @Override
    public List<Long> findIdsByClientId(Long clientProfileId) {
        return springDataRepository.findIdsByClientId(clientProfileId);
    }

    @Override
    public Page<Project> findByClientId(Long clientProfileId, List<ProjectStatus> statuses,
                                        Pageable pageable) {
        Page<ProjectJpaEntity> found = (statuses == null || statuses.isEmpty())
                ? springDataRepository.findByClientIdAndDeletedAtIsNull(clientProfileId, pageable)
                : springDataRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                clientProfileId, statuses, pageable);

        return found.map(projectMapper::toDomain);
    }

    @Override
    public Map<ProjectStatus, Long> countByStatus(Long clientProfileId) {
        Map<ProjectStatus, Long> counts = new LinkedHashMap<>();
        for (Object[] row : springDataRepository.countGroupByStatus(clientProfileId)) {
            counts.put((ProjectStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    public Page<Project> findAllForAdmin(ProjectStatus status, String keyword, Pageable pageable) {
        String normalized = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return springDataRepository.findAllForAdmin(status, normalized, pageable)
                .map(projectMapper::toDomain);
    }

    @Override
    public Optional<Position> findPositionById(Long positionId) {
        return positionRepository.findById(positionId)
                .map(projectMapper::toPositionDomain);
    }

    @Override
    public Optional<Long> findProjectIdByPositionId(Long positionId) {
        return positionRepository.findProjectIdById(positionId);
    }
}