package com.pairing.settlement.infrastructure;

import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.port.ProjectCloserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ProjectCloserPort 구현. project 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class ProjectCloserAdapter implements ProjectCloserPort {

    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public void close(Long projectId) {
        projectCommandUseCase.closeProject(projectId);
    }
}
