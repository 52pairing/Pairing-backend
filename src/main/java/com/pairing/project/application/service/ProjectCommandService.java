package com.pairing.project.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.command.CreateProjectCommand;
import com.pairing.project.application.event.RecruitingStartedEvent;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.exception.ProjectErrorCode;
import com.pairing.settlement.application.command.CreateDepositSettlementCommand;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ProjectRepository projectRepository;
    private final ClientProfileReaderPort clientProfileReaderPort;
    private final ProjectFileReaderPort projectFileReaderPort;
    private final DepositSettlementUseCase depositSettlementUseCase;
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
    public void startRecruiting(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        project.startRecruiting();
        projectRepository.updateState(project);

        // 임베딩 저장과 최초 추천은 AI 서버를 호출한다. 커밋 후로 미뤄 결제가 AI 장애에 묶이지 않게 한다.
        eventPublisher.publishEvent(new RecruitingStartedEvent(projectId));
    }

    /** 없는 fileId 를 그대로 저장하면 FK 위반으로 500 이 난다. 저장 전에 file 도메인에 존재를 확인한다. */
    private void requireAttachmentsExist(List<Long> fileIds) {
        projectFileReaderPort.getAllByIds(fileIds);
    }
}