package com.pairing.project.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.command.CreateProjectCommand;
import com.pairing.project.application.command.UpdateProjectCommand;
import com.pairing.project.application.event.ProjectUpdatedEvent;
import com.pairing.project.application.event.RecruitingStartedEvent;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.exception.ProjectErrorCode;
import com.pairing.settlement.application.command.CreateDepositSettlementCommand;
import com.pairing.settlement.application.command.CreateSuccessFeeSettlementCommand;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import com.pairing.settlement.application.usecase.SuccessFeeSettlementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 등록·수정.
 *
 * <p>account 컨텍스트는 {@link ClientProfileReaderPort} 로만 조회한다.
 * client_profile 테이블이나 account 의 리포지토리를 직접 읽지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectCommandService implements ProjectCommandUseCase {

    /** 취소·종료된 프로젝트를 보관하는 기간. (정책 P52) */
    private static final int RETENTION_YEARS = 5;

    private final ProjectRepository projectRepository;
    private final ClientProfileReaderPort clientProfileReaderPort;
    private final ProjectFileReaderPort projectFileReaderPort;
    private final DepositSettlementUseCase depositSettlementUseCase;
    private final SuccessFeeSettlementUseCase successFeeSettlementUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Long create(CreateProjectCommand command) {
        ClientProfileReaderPort.ClientProfileView client =
                clientProfileReaderPort.getByAccountId(command.accountId());

        requireAttachmentsExist(command.fileIds());

        // 근무 장소는 상주일 때만 의미가 있다. 판단은 도메인이 하고 여기서는 후보만 넘긴다.
        String clientAddress = command.workStyle() == WorkStyle.ONSITE
                ? client.address()
                : null;

        Project project = Project.create(
                client.clientProfileId(),
                command.title(),
                command.startDesiredDate(),
                command.startNegotiable(),
                command.periodValue(),
                command.periodUnit(),
                command.budgetAmount(),
                command.workStyle(),
                command.workForm(),
                clientAddress,
                command.currentSituation(),
                command.mainTask(),
                command.detailScope(),
                command.extraNote(),
                command.positions(),
                command.fileIds());

        Long projectId = projectRepository.save(project).getId();

        // 착수금은 등록 완료 시점에 발생한다. 결제 버튼이 바로 활성화되어야 한다. (P27·P32)
        depositSettlementUseCase.createClientDeposit(new CreateDepositSettlementCommand(
                projectId, command.accountId(), command.budgetAmount(), client.grade()));

        return projectId;
    }

    @Override
    public void update(UpdateProjectCommand command) {
        ClientProfileReaderPort.ClientProfileView client =
                clientProfileReaderPort.getByAccountId(command.accountId());

        Project project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!project.isOwnedBy(client.clientProfileId())) {
            throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
        }
        requireAttachmentsExist(command.fileIds());

        boolean beforePayment = project.isHeadcountChangeable();

        // 상태별로 무엇을 바꿀 수 있는지는 도메인이 판정한다.
        project.update(
                command.title(),
                command.startDesiredDate(),
                command.startNegotiable(),
                command.periodValue(),
                command.periodUnit(),
                command.budgetAmount(),
                command.workStyle(),
                command.workForm(),
                client.address(),
                command.currentSituation(),
                command.mainTask(),
                command.detailScope(),
                command.extraNote(),
                command.positions(),
                command.fileIds());

        projectRepository.updateDetail(project);

        if (beforePayment) {
            // 아직 결제 전이라 예산이 바뀌었을 수 있다. 미결제 착수금을 새 예산으로 다시 계산한다.
            depositSettlementUseCase.recalculateClientDeposit(
                    command.projectId(), command.budgetAmount(), client.grade());
            return;
        }

        // 모집이 시작된 뒤라면 저장된 포지션 임베딩이 낡는다. 커밋 후 매칭에 알린다.
        eventPublisher.publishEvent(new ProjectUpdatedEvent(command.projectId()));
    }

    @Override
    public void complete(Long projectId, Long accountId) {
        ClientProfileReaderPort.ClientProfileView client =
                clientProfileReaderPort.getByAccountId(accountId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!project.isOwnedBy(client.clientProfileId())) {
            throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
        }

        project.requestCompletion();
        projectRepository.updateState(project);

        // 성공보수는 완료 대기로 넘어간 시점에 발생한다. 결제 버튼이 바로 활성화되어야 한다. (P30)
        // TODO: 계약 도메인이 붙으면 기준 금액을 예산이 아니라 계약 금액 합계로 바꾼다.
        successFeeSettlementUseCase.createClientSuccessFee(new CreateSuccessFeeSettlementCommand(
                projectId, accountId, project.getBudgetAmount(), client.grade()));
    }

    @Override
    public boolean terminate(Long projectId, Long accountId) {
        Project project = loadOwned(projectId, accountId);

        boolean penaltyExpected = project.terminate(LocalDate.now().plusYears(RETENTION_YEARS));
        // 프로젝트 상태와 포지션 상태가 함께 바뀐다. 수정용 경로는 상태를 옮기지 않는다.
        projectRepository.updateStateWithPositions(project);

        // TODO: 계약 도메인이 붙으면 진행 중인 계약을 여기서 파기한다.
        //       계약만 살아남으면 안 되므로 같은 트랜잭션에서 직접 호출한다.
        return penaltyExpected;
    }

    @Override
    public void closeRecruit(Long projectId, Long accountId) {
        Project project = loadOwned(projectId, accountId);

        project.closeRecruit(LocalDate.now().plusYears(RETENTION_YEARS));
        // 프로젝트 상태와 포지션 상태가 함께 바뀐다. 수정용 경로는 상태를 옮기지 않는다.
        projectRepository.updateStateWithPositions(project);
    }

    @Override
    public void extendRecruit(Long projectId, Long accountId) {
        Project project = loadOwned(projectId, accountId);

        project.extendRecruit();
        projectRepository.updateState(project);
    }

    /** 소유자 확인까지 마친 프로젝트. 상태 전이 API 가 공통으로 쓴다. */
    private Project loadOwned(Long projectId, Long accountId) {
        Long clientProfileId = clientProfileReaderPort.getByAccountId(accountId).clientProfileId();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!project.isOwnedBy(clientProfileId)) {
            throw new BusinessException(ProjectErrorCode.NOT_PROJECT_OWNER);
        }
        return project;
    }

    @Override
    public void startRecruiting(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.startRecruiting();
        projectRepository.updateState(project);

        // 임베딩 저장과 최초 추천은 AI 서버를 호출한다. 커밋 후로 미뤄 결제가 AI 장애에 묶이지 않게 한다.
        eventPublisher.publishEvent(new RecruitingStartedEvent(projectId));
    }

    @Override
    public void startNegotiating(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.startNegotiating();
        projectRepository.updateState(project);
    }

    @Override
    public void awaitContract(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.awaitContract();
        projectRepository.updateState(project);
    }

    @Override
    public void syncStage(Long projectId, boolean hasContractPending, boolean hasNegotiating) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.syncStage(hasContractPending, hasNegotiating);
        projectRepository.updateState(project);
    }

    @Override
    public void confirmPosition(Long positionId) {
        Long projectId = projectRepository.findProjectIdByPositionId(positionId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.confirmPosition(positionId);
        // 인원이 다 차면 그 포지션이 CLOSED 로 닫힌다. 포지션 상태까지 옮기는 경로를 쓴다.
        projectRepository.updateStateWithPositions(project);
    }

    @Override
    public void closeProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.close(LocalDate.now().plusYears(RETENTION_YEARS));
        // 프로젝트 상태와 포지션 상태가 함께 바뀐다. 수정용 경로는 상태를 옮기지 않는다.
        projectRepository.updateStateWithPositions(project);
    }

    /** 없는 fileId 를 그대로 저장하면 FK 위반으로 500 이 난다. 저장 전에 file 도메인에 존재를 확인한다. */
    private void requireAttachmentsExist(List<Long> fileIds) {
        projectFileReaderPort.getAllByIds(fileIds);
    }
}