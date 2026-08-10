package com.pairing.matching.infrastructure.directory;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@link ProjectDirectoryPort}의 실제 구현. project 도메인과 account 도메인의 인바운드 포트를 조합한다. */
@Component
@RequiredArgsConstructor
public class ProjectDirectoryAdapter implements ProjectDirectoryPort {

    private final ProjectQueryUseCase projectQueryUseCase;
    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    public boolean isOwnedByAccount(Long projectId, Long accountId) {
        return projectQueryUseCase.isOwnedBy(projectId, accountId);
    }

    @Override
    public List<Long> findProjectIdsOwnedByAccount(Long accountId) {
        return projectQueryUseCase.findProjectIdsByAccountId(accountId);
    }

    @Override
    public Long findClientAccountId(Long projectId) {
        Long clientProfileId = projectQueryUseCase.findClientProfileId(projectId);
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(ClientProfile::getAccountId)
                .orElse(null);
    }

    @Override
    public int findHeadcount(Long positionId) {
        return projectQueryUseCase.findHeadcount(positionId);
    }

    @Override
    public ProjectStatus findStatus(Long projectId) {
        return projectQueryUseCase.findStatus(projectId);
    }

    @Override
    public List<Long> findPositionIds(Long projectId) {
        return projectQueryUseCase.getById(projectId).getPositions().stream()
                .map(Position::getId)
                .toList();
    }

    @Override
    public String findCompanyProfile(Long projectId) {
        Long clientProfileId = projectQueryUseCase.findClientProfileId(projectId);
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(profile -> profile.getBusinessField().getLabel() + " · " + profile.getEmployeeCount().getLabel())
                .orElse(null);
    }

    @Override
    public ProjectPositionSummary findPositionSummary(Long projectId, Long positionId) {
        com.pairing.project.application.result.ProjectPositionSummary source =
                projectQueryUseCase.findProjectPositionSummary(projectId, positionId);
        Long clientProfileId = projectQueryUseCase.findClientProfileId(projectId);
        ClientProfile clientProfile = accountQueryUseCase.findClientProfileById(clientProfileId).orElse(null);
        String companyName = clientProfile != null ? clientProfile.getCompanyName() : null;
        String companyProfile = findCompanyProfile(projectId);

        String workLabel = source.workStyle().getLabel() + " · " + source.workForm().getLabel();
        String periodLabel = source.periodValue() + source.periodUnit().getLabel();

        return new ProjectPositionSummary(
                source.projectId(),
                source.title(),
                companyName,
                companyProfile,
                source.jobRole(),
                source.skills(),
                source.minCareerYears(),
                workLabel,
                periodLabel,
                source.periodValue(),
                source.periodUnit(),
                source.startDesiredDate(),
                source.budgetAmount(),
                source.headcount(),
                source.totalHeadcount(),
                source.mainTask(),
                source.detailScope(),
                source.extraNote()
        );
    }
}
