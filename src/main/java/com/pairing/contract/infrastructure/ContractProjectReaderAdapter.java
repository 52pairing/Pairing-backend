package com.pairing.contract.infrastructure;

import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.global.exception.BusinessException;
import com.pairing.project.application.result.ProjectPositionSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ContractProjectReaderPort 구현. project 도메인의 인바운드 포트를 호출한다.
 *
 * <p>프로젝트나 포지션이 지워진 계약도 열람은 돼야 한다. 계약은 5년 보관 대상이라
 * 원본이 사라져도 이력이 남는다. 조회 실패는 빈 뷰로 흡수하고 다른 예외는 전파한다.
 */
@Component
@RequiredArgsConstructor
public class ContractProjectReaderAdapter implements ContractProjectReaderPort {

    private final ProjectQueryUseCase projectQueryUseCase;

    @Override
    public ProjectContractView findForContract(Long projectId) {
        Project project = projectQueryUseCase.getById(projectId);

        return new ProjectContractView(
                project.getClientId(),
                project.getWorkStyle(),
                project.getWorkForm(),
                project.getWorkLocation(),
                project.getStartDesiredDate(),
                project.getPeriodValue(),
                project.getPeriodUnit(),
                project.getMainTask(),
                project.getDetailScope());
    }

    @Override
    public ProjectView findByPositionId(Long positionId) {
        try {
            ProjectPositionSummary summary = projectQueryUseCase.findProjectPositionSummary(positionId);
            return new ProjectView(summary.title(), summary.jobRole());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ProjectErrorCode.PROJECT_NOT_FOUND
                    || e.getErrorCode() == ProjectErrorCode.POSITION_NOT_FOUND) {
                return ProjectView.EMPTY;
            }
            throw e;
        }
    }
}
