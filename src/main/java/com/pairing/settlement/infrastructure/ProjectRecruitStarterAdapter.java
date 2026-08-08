package com.pairing.settlement.infrastructure;

import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.port.ProjectRecruitStarterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ProjectRecruitStarterPort 구현. project 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class ProjectRecruitStarterAdapter implements ProjectRecruitStarterPort {

    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public void startRecruiting(Long projectId) {
        projectCommandUseCase.startRecruiting(projectId);
    }
}
