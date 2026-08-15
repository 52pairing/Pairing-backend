package com.pairing.matching.infrastructure.directory;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectContent;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
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
    public ProjectPositionSummary findPositionSummary(Long positionId) {
        // projectId 를 모르는 상태이므로 포지션만으로 찾아서 projectId 를 얻어낸 뒤,
        // 나머지 조립은 기존 경로를 그대로 탄다(두 벌로 만들면 한쪽만 고쳐진다).
        return findPositionSummary(
                projectQueryUseCase.findProjectPositionSummary(positionId).projectId(), positionId);
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
                source.currentSituation(),
                source.mainTask(),
                source.detailScope(),
                source.extraNote()
        );
    }

    /**
     * 요약본이 아니라 프로젝트 엔티티에서 직접 읽는다. 근무 장소·시작일 협의 여부가 요약본에 없어서다.
     *
     * <p>{@code getDetail} 이 아니라 {@code getById} 를 쓴다. 그쪽은 첨부 자료와 정산 ID까지 같이
     * 읽어오는데 둘 다 프리랜서에게 안 내보내는 값이라 조회만 늘어난다.
     */
    @Override
    public ProjectContent findProjectContent(Long projectId) {
        Project project = projectQueryUseCase.getById(projectId);
        return new ProjectContent(
                project.getCurrentSituation(),
                project.isStartNegotiable(),
                project.getPeriodValue(),
                project.getPeriodUnit(),
                project.getDetailScope(),
                project.getExtraNote(),
                project.getWorkLocation()
        );
    }
}
