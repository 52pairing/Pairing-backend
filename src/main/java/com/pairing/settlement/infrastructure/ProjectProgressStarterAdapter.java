package com.pairing.settlement.infrastructure;

import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.port.ProjectProgressStarterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ProjectProgressStarterPort 구현. project 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class ProjectProgressStarterAdapter implements ProjectProgressStarterPort {

    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public boolean startProgress(Long projectId) {
        return projectCommandUseCase.startProgress(projectId);
    }
}
