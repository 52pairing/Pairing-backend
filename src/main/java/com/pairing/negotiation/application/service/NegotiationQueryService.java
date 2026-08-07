package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NegotiationQueryService implements NegotiationQueryUseCase {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationMessageRepository messageRepository;
    private final ProjectReaderPort projectReaderPort;
    private final PartyProfilePort partyProfilePort;
    private final PartyNameReaderPort partyNameReaderPort;
    private final NegotiationViewerResolver viewerResolver;

    @Override
    public NegotiationView getDetail(Long negotiationId, Long accountId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));

        Optional<ProjectView> project = projectReaderPort.findById(negotiation.getProjectId());
        Long clientProfileId = project.map(ProjectView::clientProfileId).orElse(null);

        PartyRole role = viewerResolver.resolve(accountId, negotiation.getFreelancerId(), clientProfileId);
        String title = project.map(ProjectView::title).orElse(null);

        return toView(negotiation, role, title, clientProfileId);
    }

    @Override
    public List<NegotiationMessage> findMessages(Long negotiationId, Long accountId) {
        assertParticipant(negotiationId, accountId);
        return messageRepository.findByNegotiationId(negotiationId);
    }

    @Override
    public NegotiationLogVerifier.Result verifyLog(Long negotiationId, Long accountId) {
        assertParticipant(negotiationId, accountId);
        return NegotiationLogVerifier.verify(messageRepository.findByNegotiationId(negotiationId));
    }

    /** 협상 존재 + 당사자 검증(NG_001/NG_002). */
    private void assertParticipant(Long negotiationId, Long accountId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));
        Long clientProfileId = projectReaderPort.findById(negotiation.getProjectId())
                .map(ProjectView::clientProfileId).orElse(null);
        viewerResolver.resolve(accountId, negotiation.getFreelancerId(), clientProfileId);
    }

    @Override
    public Page<NegotiationView> findMine(Long accountId, Long projectId, NegotiationStatus status,
                                          Pageable pageable) {
        if (projectId != null) {
            return findClientTab(accountId, projectId, status, pageable);
        }
        return findFreelancerList(accountId, status, pageable);
    }

    /** 클라 협상 탭: 프로젝트 소유자만. role·title·회사명은 프로젝트 하나로 공유된다. */
    private Page<NegotiationView> findClientTab(Long accountId, Long projectId, NegotiationStatus status,
                                                Pageable pageable) {
        ProjectView project = projectReaderPort.findById(projectId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        Long myClientProfileId = partyProfilePort.findClientProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));
        if (!myClientProfileId.equals(project.clientProfileId())) {
            throw new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT);
        }

        String clientName = partyNameReaderPort.findClientCompanyName(project.clientProfileId()).orElse(null);
        return negotiationRepository.findByProjectId(projectId, status, pageable)
                .map(n -> new NegotiationView(n, PartyRole.CLIENT, project.title(), clientName,
                        partyNameReaderPort.findFreelancerName(n.getFreelancerId()).orElse(null)));
    }

    /** 프리랜서 목록: 협상마다 프로젝트가 달라 title 은 건별로 읽는다. */
    private Page<NegotiationView> findFreelancerList(Long accountId, NegotiationStatus status, Pageable pageable) {
        Long myFreelancerProfileId = partyProfilePort.findFreelancerProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        return negotiationRepository.findByFreelancerId(myFreelancerProfileId, status, pageable)
                .map(n -> {
                    Optional<ProjectView> project = projectReaderPort.findById(n.getProjectId());
                    return toView(n, PartyRole.FREELANCER,
                            project.map(ProjectView::title).orElse(null),
                            project.map(ProjectView::clientProfileId).orElse(null));
                });
    }

    /** 애그리거트 + role + 표시용 이름(회사명·프리 이름)을 조립한다. */
    private NegotiationView toView(Negotiation negotiation, PartyRole role, String title, Long clientProfileId) {
        String clientName = partyNameReaderPort.findClientCompanyName(clientProfileId).orElse(null);
        String freelancerName = partyNameReaderPort.findFreelancerName(negotiation.getFreelancerId()).orElse(null);
        return new NegotiationView(negotiation, role, title, clientName, freelancerName);
    }
}
