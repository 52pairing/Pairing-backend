package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NegotiationQueryService implements NegotiationQueryUseCase {

    private final NegotiationRepository negotiationRepository;
    private final ProjectReaderPort projectReaderPort;
    private final PartyProfilePort partyProfilePort;
    private final NegotiationViewerResolver viewerResolver;

    @Override
    public NegotiationView getDetail(Long negotiationId, Long accountId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));

        Optional<ProjectView> project = projectReaderPort.findById(negotiation.getProjectId());
        Long clientProfileId = project.map(ProjectView::clientProfileId).orElse(null);

        PartyRole role = viewerResolver.resolve(accountId, negotiation.getFreelancerId(), clientProfileId);
        String title = project.map(ProjectView::title).orElse(null);

        return new NegotiationView(negotiation, role, title);
    }

    @Override
    public List<NegotiationView> findMine(Long accountId, Long projectId, NegotiationStatus status) {
        if (projectId != null) {
            return findClientTab(accountId, projectId, status);
        }
        return findFreelancerList(accountId, status);
    }

    /** 클라 협상 탭: 프로젝트 소유자만. 조건 목록·role·title 은 프로젝트 하나로 공유된다. */
    private List<NegotiationView> findClientTab(Long accountId, Long projectId, NegotiationStatus status) {
        ProjectView project = projectReaderPort.findById(projectId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        Long myClientProfileId = partyProfilePort.findClientProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));
        if (!myClientProfileId.equals(project.clientProfileId())) {
            throw new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT);
        }

        return negotiationRepository.findByProjectId(projectId).stream()
                .filter(n -> matchesStatus(n, status))
                .map(n -> new NegotiationView(n, PartyRole.CLIENT, project.title()))
                .toList();
    }

    /** 프리랜서 목록: 협상마다 프로젝트가 달라 title 은 건별로 읽는다. */
    private List<NegotiationView> findFreelancerList(Long accountId, NegotiationStatus status) {
        Long myFreelancerProfileId = partyProfilePort.findFreelancerProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        return negotiationRepository.findByFreelancerId(myFreelancerProfileId).stream()
                .filter(n -> matchesStatus(n, status))
                .map(n -> {
                    String title = projectReaderPort.findById(n.getProjectId())
                            .map(ProjectView::title).orElse(null);
                    return new NegotiationView(n, PartyRole.FREELANCER, title);
                })
                .toList();
    }

    private boolean matchesStatus(Negotiation negotiation, NegotiationStatus status) {
        return status == null || negotiation.getStatus() == status;
    }
}
