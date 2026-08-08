package com.pairing.project.infrastructure.persistence;

import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.infrastructure.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 프로젝트 리포지토리 어댑터.
 *
 * <p>포지션 저장은 Project 애그리거트의 cascade 로 처리한다. 포지션 리포지토리는
 * 매칭이 projectId 없이 포지션만 조회할 때만 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final SpringDataProjectRepository springDataRepository;
    private final SpringDataProjectPositionRepository positionRepository;
    private final ProjectMapper projectMapper;

    @Override
    public Project save(Project project) {
        ProjectJpaEntity saved = springDataRepository.save(projectMapper.toJpaEntity(project));
        return projectMapper.toDomain(saved);
    }

    @Override
    public Optional<Project> findById(Long projectId) {
        return springDataRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(projectMapper::toDomain);
    }

    @Override
    public Optional<Long> findClientIdById(Long projectId) {
        return springDataRepository.findClientIdById(projectId);
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
}