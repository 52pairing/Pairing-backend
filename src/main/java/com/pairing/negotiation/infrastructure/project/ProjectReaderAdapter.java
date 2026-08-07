package com.pairing.negotiation.infrastructure.project;

import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProjectReaderAdapter implements ProjectReaderPort {

    private final SpringDataProjectReadRepository projectReadRepository;

    @Override
    public Optional<ProjectView> findById(Long projectId) {
        return projectReadRepository.findById(projectId)
                .map(p -> new ProjectView(p.getId(), p.getClientId(), p.getTitle(),
                        p.getBudgetAmount(), p.getWorkStyle(), p.getWorkForm(),
                        p.getStartDesiredDate(), p.isStartNegotiable()));
    }
}
