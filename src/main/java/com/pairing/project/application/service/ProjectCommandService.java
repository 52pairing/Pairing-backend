package com.pairing.project.application.service;

import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.command.CreateProjectCommand;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public Long create(CreateProjectCommand command) {
        ClientProfileReaderPort.ClientProfileView client =
                clientProfileReaderPort.getByAccountId(command.accountId());

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

        return projectRepository.save(project).getId();
    }
}