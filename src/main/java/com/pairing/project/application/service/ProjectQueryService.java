package com.pairing.project.application.service;

import com.pairing.file.exception.FileErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort;
import com.pairing.project.application.port.SettlementReaderPort;
import com.pairing.project.application.result.ProjectAttachment;
import com.pairing.project.application.result.ProjectDetail;
import com.pairing.project.application.result.ProjectPositionSummary;
import com.pairing.project.application.result.ProjectSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.model.ProjectTab;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 프로젝트 조회.
 *
 * <p>account 컨텍스트는 {@link ClientProfileReaderPort} 로만 조회한다.
 * accountId 를 client_profile.id 로 바꾸는 책임이 이 계층에 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectQueryService implements ProjectQueryUseCase {

    private final ProjectRepository projectRepository;
    private final ClientProfileReaderPort clientProfileReaderPort;
    private final ProjectFileReaderPort projectFileReaderPort;
    private final SettlementReaderPort settlementReaderPort;

    @Override
    public Project getById(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    public Project getByIdForOwner(Long projectId, Long accountId) {
        Project project = getById(projectId);

        if (!project.isOwnedBy(resolveClientProfileId(accountId))) {
            throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
        }
        return project;
    }

    @Override
    public boolean isOwnedBy(Long projectId, Long accountId) {
        Long clientProfileId = resolveClientProfileId(accountId);
        return projectRepository.findClientIdById(projectId)
                .map(clientProfileId::equals)
                .orElse(false);
    }

    @Override
    public List<Long> findProjectIdsByAccountId(Long accountId) {
        return projectRepository.findIdsByClientId(resolveClientProfileId(accountId));
    }

    @Override
    public Long findClientProfileId(Long projectId) {
        return projectRepository.findClientIdById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    public int findHeadcount(Long positionId) {
        return projectRepository.findPositionById(positionId)
                .map(Position::getHeadcount)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));
    }

    @Override
    public ProjectStatus findStatus(Long projectId) {
        return projectRepository.findStatusById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    public ProjectPositionSummary findProjectPositionSummary(Long projectId, Long positionId) {
        Project project = getById(projectId);
        Position position = project.getPositions().stream()
                .filter(p -> positionId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));

        return toSummary(project, position);
    }

    @Override
    public ProjectPositionSummary findProjectPositionSummary(Long positionId) {
        Long projectId = projectRepository.findProjectIdByPositionId(positionId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));

        return findProjectPositionSummary(projectId, positionId);
    }

    @Override
    public List<ProjectPositionSummary> findPositionSummaries(Long projectId) {
        Project project = getById(projectId);

        return project.getPositions().stream()
                .map(position -> toSummary(project, position))
                .toList();
    }

    private ProjectPositionSummary toSummary(Project project, Position position) {
        return new ProjectPositionSummary(
                project.getId(),
                project.getTitle(),
                position.getJobRole(),
                position.getSkills(),
                position.getMinCareerYears(),
                position.getHeadcount(),
                project.getWorkStyle(),
                project.getWorkForm(),
                project.getPeriodValue(),
                project.getPeriodUnit(),
                project.getStartDesiredDate(),
                project.getBudgetAmount(),
                project.getTotalHeadcount(),
                project.getCurrentSituation(),
                project.getMainTask(),
                project.getDetailScope(),
                project.getExtraNote());
    }

    @Override
    public ProjectDetail getDetail(Long projectId) {
        return toDetail(getById(projectId));
    }

    @Override
    public ProjectDetail getDetailForOwner(Long projectId, Long accountId) {
        return toDetail(getByIdForOwner(projectId, accountId));
    }

    /**
     * 순서가 중요하다. 소유자 확인 -&gt; 첨부 소속 확인 -&gt; 읽기 순으로 좁힌다.
     *
     * <p>읽기를 먼저 하면 남의 파일을 스토리지에서 꺼낸 뒤에 버리게 된다. 응답으로는 안 나가지만
     * 응답 시간 차이로 그 fileId 가 실존하는지 알 수 있다.
     */
    @Override
    public ProjectAttachment downloadAttachment(Long projectId, Long fileId, Long accountId) {
        Project project = getByIdForOwner(projectId, accountId);

        if (fileId == null || !project.getFileIds().contains(fileId)) {
            throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
        }

        // 지워진 파일이면 여기서 FI_001 이 올라온다. 목록에는 남아 있는데 스토리지에만 없는 경우다.
        ProjectFileReaderPort.ProjectFileView meta = projectFileReaderPort.getAllByIds(List.of(fileId)).get(0);
        byte[] content = projectFileReaderPort.readContent(fileId)
                .orElseThrow(() -> new BusinessException(FileErrorCode.FILE_NOT_FOUND));

        return new ProjectAttachment(content, meta.originalName());
    }

    @Override
    public Page<ProjectSummary> findMine(Long accountId, ProjectTab tab, Pageable pageable) {
        List<ProjectStatus> statuses = tab == null ? List.of() : tab.getStatuses();

        return projectRepository.findByClientId(resolveClientProfileId(accountId), statuses, pageable)
                .map(project -> new ProjectSummary(project,
                        settlementReaderPort.findPayableSettlementId(project.getId()).orElse(null)));
    }

    @Override
    public Map<ProjectTab, Long> countMyTabs(Long accountId) {
        Map<ProjectStatus, Long> byStatus = projectRepository.countByStatus(resolveClientProfileId(accountId));

        // 건수가 0인 탭도 키로 넣는다. 화면이 탭을 전부 그려야 한다.
        Map<ProjectTab, Long> byTab = new EnumMap<>(ProjectTab.class);
        for (ProjectTab tab : ProjectTab.values()) {
            long count = tab.getStatuses().stream()
                    .mapToLong(status -> byStatus.getOrDefault(status, 0L))
                    .sum();
            byTab.put(tab, count);
        }
        return byTab;
    }

    private ProjectDetail toDetail(Project project) {
        return new ProjectDetail(
                project,
                projectFileReaderPort.getAllByIds(project.getFileIds()),
                settlementReaderPort.findPayableSettlementId(project.getId()).orElse(null));
    }

    private Long resolveClientProfileId(Long accountId) {
        return clientProfileReaderPort.getByAccountId(accountId).clientProfileId();
    }
}
