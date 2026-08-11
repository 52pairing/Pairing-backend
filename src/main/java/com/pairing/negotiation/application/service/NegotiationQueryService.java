package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.ChatRoomLookupPort;
import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.result.AgreedNegotiationView;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final ChatRoomLookupPort chatRoomLookupPort;

    @Override
    public NegotiationView getDetail(Long negotiationId, Long accountId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));

        Optional<ProjectView> project = projectReaderPort.findById(negotiation.getProjectId());
        Long clientProfileId = project.map(ProjectView::clientProfileId).orElse(null);

        PartyRole role = viewerResolver.resolve(accountId, negotiation.getFreelancerId(), clientProfileId);
        String title = project.map(ProjectView::title).orElse(null);
        Long chatRoomId = chatRoomLookupPort.findChatRoomIdByNegotiationId(negotiationId).orElse(null);

        return detailView(negotiation, role, title, clientProfileId, chatRoomId);
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

    @Override
    public long countWaitingForMe(Long accountId) {
        if (accountId == null) {
            return 0L;
        }
        // 한 계정이 클라·프리 양쪽일 수 있다. 각 역할로 센 뒤 합친다(둘 다 아니면 0).
        long asFreelancer = partyProfilePort.findFreelancerProfileIdByAccountId(accountId)
                .map(negotiationRepository::countWaitingForFreelancer)
                .orElse(0L);
        long asClient = negotiationRepository.countWaitingForClient(
                projectReaderPort.findMyProjectIds(accountId));
        return asFreelancer + asClient;
    }

    @Override
    public AgreedNegotiationView getAgreedForContract(Long negotiationId) {
        Negotiation negotiation = negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));
        // 서버간 호출이라 뷰어 검증이 없다. 대신 타결 상태를 확인해 확정 안 된 조건이 계약으로 새지 않게 한다.
        if (negotiation.getStatus() != NegotiationStatus.AGREED) {
            throw new BusinessException(NegotiationErrorCode.NOT_AGREED);
        }
        return AgreedNegotiationView.from(negotiation);
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

        return negotiationRepository.findByProjectId(projectId, status, pageable)
                .map(n -> summaryView(n, PartyRole.CLIENT, project.title(), project.clientProfileId()));
    }

    /** 프리랜서 목록: 협상마다 프로젝트가 달라 title 은 건별로 읽는다. */
    private Page<NegotiationView> findFreelancerList(Long accountId, NegotiationStatus status, Pageable pageable) {
        Long myFreelancerProfileId = partyProfilePort.findFreelancerProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        return negotiationRepository.findByFreelancerId(myFreelancerProfileId, status, pageable)
                .map(n -> {
                    Optional<ProjectView> project = projectReaderPort.findById(n.getProjectId());
                    return summaryView(n, PartyRole.FREELANCER,
                            project.map(ProjectView::title).orElse(null),
                            project.map(ProjectView::clientProfileId).orElse(null));
                });
    }

    /**
     * 상세 뷰: 조건별 현재 AI 제안(값·근거) + 채팅방 ID 를 채운다.
     *
     * <p>제안값은 <b>뷰어 기준 '상대가 낸 것'</b>만 고른다. 내 편 대리인이 부른 값을 승인 패널에
     * 띄우면 자기 요구를 자기가 수락하게 되고, 상대는 동의한 적 없는 조건이 확정된다.
     * 수락 처리(NegotiationLoopService)도 같은 기준으로 락할 값을 고른다.
     */
    private NegotiationView detailView(Negotiation negotiation, PartyRole role, String title, Long clientProfileId,
                                       Long chatRoomId) {
        Map<Long, NegotiationView.ConditionProposal> proposals = new HashMap<>();
        for (NegotiationCondition c : negotiation.getConditions()) {
            messageRepository.findLatestProposalExcluding(negotiation.getId(), c.getId(), role.ownSenders())
                    .ifPresent(m -> proposals.put(c.getId(),
                            new NegotiationView.ConditionProposal(m.getProposedValue(), m.getReason())));
        }
        return NegotiationView.forDetail(negotiation, role, title,
                clientName(clientProfileId), freelancerName(negotiation), chatRoomId,
                isWaitingFor(negotiation, role), proposals);
    }

    /** 목록 뷰: 마지막 제안(주체·시각) + '내 응답 필요' 여부를 채운다. */
    private NegotiationView summaryView(Negotiation negotiation, PartyRole role, String title, Long clientProfileId) {
        NegotiationMessage lastProposal = messageRepository.findLatestProposal(negotiation.getId()).orElse(null);
        return NegotiationView.forSummary(negotiation, role, title,
                clientName(clientProfileId), freelancerName(negotiation), isWaitingFor(negotiation, role),
                lastProposal != null ? lastProposal.getSenderType() : null,
                lastProposal != null ? lastProposal.getCreatedAt() : null);
    }

    /** '내 응답 필요': 진행 중 + 이번 라운드에 AI 제안이 있는데 내 응답이 아직 없을 때. */
    private boolean isWaitingFor(Negotiation negotiation, PartyRole role) {
        if (negotiation.getStatus() != NegotiationStatus.IN_PROGRESS) {
            return false;
        }
        int round = negotiation.getTotalRound();
        if (round <= 0 || messageRepository.countProposalsInRound(negotiation.getId(), round) == 0) {
            return false;
        }
        SenderType mySender = role == PartyRole.CLIENT ? SenderType.CLIENT : SenderType.FREELANCER;
        return messageRepository.countResponsesInRound(negotiation.getId(), mySender, round) == 0;
    }

    private String clientName(Long clientProfileId) {
        return partyNameReaderPort.findClientCompanyName(clientProfileId).orElse(null);
    }

    private String freelancerName(Negotiation negotiation) {
        return partyNameReaderPort.findFreelancerName(negotiation.getFreelancerId()).orElse(null);
    }
}
