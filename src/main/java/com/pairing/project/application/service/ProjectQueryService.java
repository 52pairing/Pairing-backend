package com.pairing.project.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort;
import com.pairing.project.application.port.SettlementReaderPort;
import com.pairing.project.application.result.ProjectDetail;
import com.pairing.project.application.result.ProjectPositionSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public ProjectPositionSummary findProjectPositionSummary(Long projectId, Long positionId) {
        Project project = getById(projectId);
        Position position = project.getPositions().stream()
                .filter(p -> positionId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));

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
                project.getCurrentSituation(),
                project.getMainTask());
    }

    @Override
    public ProjectDetail getDetail(Long projectId) {
        return toDetail(getById(projectId));
    }

    @Override
    public ProjectDetail getDetailForOwner(Long projectId, Long accountId) {
        return toDetail(getByIdForOwner(projectId, accountId));
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
