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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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

    /**
     * 당사자 협상방의 대화 로그.
     *
     * <p><b>감사 기록(AUDIT)은 뺀다.</b> 타결 시점 최종 조건 스냅샷 같은 것으로, 기계가 파싱할
     * 형태(공백 없는 한 줄)라 사람이 읽을 물건이 아니다. 실제로 그게 협상방에 그대로 노출돼
     * 화면을 옆으로 밀어 가로 스크롤을 만들었다(2026-08-12).
     *
     * <p>저장은 그대로 두고 여기서만 거른다 — 해시 체인은 손대지 않으므로 증거 능력에 영향이
     * 없고, {@link #verifyLog} 와 관리자 화면은 계속 전체 로그를 본다.
     */
    @Override
    public List<NegotiationMessage> findMessages(Long negotiationId, Long accountId) {
        assertParticipant(negotiationId, accountId);
        return messageRepository.findByNegotiationId(negotiationId).stream()
                .filter(message -> !message.isAudit())
                .toList();
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

        // 클라 탭은 프로젝트가 하나 → title·clientProfileId 는 카드마다 같은 값을 쓴다.
        Page<Negotiation> page = negotiationRepository.findByProjectId(projectId, status, pageable);
        return buildSummaryPage(page, PartyRole.CLIENT, n -> project.title(), n -> project.clientProfileId());
    }

    /**
     * 프리랜서 목록: 협상마다 프로젝트가 다르다.
     *
     * <p>예전엔 카드마다 프로젝트·이름·최신제안·응답대기 여부를 각각 조회해 페이지 크기(기본 10)만큼
     * 쿼리가 쏟아지는 N+1 이었다. 지금은 페이지의 프로젝트 ID·협상 ID 를 모아 <b>종류별로 한 번씩</b>만
     * 읽고({@link #buildSummaryPage}) 카드를 맵에서 조립한다.
     */
    private Page<NegotiationView> findFreelancerList(Long accountId, NegotiationStatus status, Pageable pageable) {
        Long myFreelancerProfileId = partyProfilePort.findFreelancerProfileIdByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT));

        Page<Negotiation> page = negotiationRepository.findByFreelancerId(myFreelancerProfileId, status, pageable);
        List<Long> projectIds = page.getContent().stream()
                .map(Negotiation::getProjectId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ProjectReaderPort.ProjectCardInfo> projects = projectReaderPort.findCardInfoByIds(projectIds);

        return buildSummaryPage(page, PartyRole.FREELANCER,
                n -> cardInfo(projects, n).map(ProjectReaderPort.ProjectCardInfo::title).orElse(null),
                n -> cardInfo(projects, n).map(ProjectReaderPort.ProjectCardInfo::clientProfileId).orElse(null));
    }

    private Optional<ProjectReaderPort.ProjectCardInfo> cardInfo(
            Map<Long, ProjectReaderPort.ProjectCardInfo> projects, Negotiation n) {
        return Optional.ofNullable(projects.get(n.getProjectId()));
    }

    /**
     * 목록 카드 조립. 페이지의 협상들에 필요한 부수 데이터를 <b>종류별 한 번</b>씩만 읽어(배치) 맵으로 만든 뒤,
     * 카드를 그 맵에서 채운다. title·clientProfileId 만 호출부가 정해 주고(프리=프로젝트별, 클라=단일 프로젝트),
     * 최신 제안·응답대기·당사자 이름은 여기서 공통으로 배치 조회한다.
     */
    private Page<NegotiationView> buildSummaryPage(Page<Negotiation> page, PartyRole role,
                                                   Function<Negotiation, String> titleFn,
                                                   Function<Negotiation, Long> clientProfileIdFn) {
        List<Negotiation> negs = page.getContent();
        List<Long> negIds = negs.stream().map(Negotiation::getId).toList();

        Map<Long, NegotiationMessage> lastProposals =
                messageRepository.findLatestProposalsByNegotiationIds(negIds);
        Set<Long> hasProposalInRound = messageRepository.negotiationIdsWithProposalInCurrentRound(negIds);
        SenderType mySender = role == PartyRole.CLIENT ? SenderType.CLIENT : SenderType.FREELANCER;
        Set<Long> hasMyResponseInRound =
                messageRepository.negotiationIdsWithResponseInCurrentRound(negIds, mySender);

        List<Long> clientProfileIds = negs.stream()
                .map(clientProfileIdFn).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> clientNames = partyNameReaderPort.findClientCompanyNames(clientProfileIds);
        List<Long> freelancerIds = negs.stream()
                .map(Negotiation::getFreelancerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> freelancerNames = partyNameReaderPort.findFreelancerNames(freelancerIds);

        return page.map(n -> {
            Long clientProfileId = clientProfileIdFn.apply(n);
            NegotiationMessage lastProposal = lastProposals.get(n.getId());
            boolean waiting = n.getStatus() == NegotiationStatus.IN_PROGRESS
                    && n.getTotalRound() > 0
                    && hasProposalInRound.contains(n.getId())
                    && !hasMyResponseInRound.contains(n.getId());
            return NegotiationView.forSummary(n, role, titleFn.apply(n),
                    clientProfileId == null ? null : clientNames.get(clientProfileId),
                    freelancerNames.get(n.getFreelancerId()), waiting,
                    lastProposal != null ? lastProposal.getSenderType() : null,
                    lastProposal != null ? lastProposal.getCreatedAt() : null);
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
