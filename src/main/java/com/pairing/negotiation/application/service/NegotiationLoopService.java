package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.event.NegotiationEvent;
import com.pairing.negotiation.application.event.NegotiationEvent.NegotiationEventType;
import com.pairing.negotiation.application.port.out.NegotiationEventPort;
import com.pairing.negotiation.application.port.out.NegotiationProposalPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class NegotiationLoopService implements NegotiationLoopUseCase {

    private final NegotiationRepository negotiationRepository;
    private final NegotiationMessageRepository messageRepository;
    private final ProjectReaderPort projectReaderPort;
    private final NegotiationViewerResolver viewerResolver;
    private final NegotiationEventPort eventPort;
    private final NegotiationProposalPort proposalPort;

    @Override
    public void start(Long negotiationId, Long accountId, List<FloorInput> floors) {
        Negotiation negotiation = load(negotiationId);
        PartyRole role = resolveRole(negotiation, accountId);
        ensureInProgress(negotiation);

        // 요청자 본인 쪽 마지노선 저장(쟁점별).
        for (FloorInput floor : floors) {
            findByType(negotiation, floor.conditionType()).submitFloor(role, floor.value());
        }

        // 초기 제안(라운드 1) 생성.
        negotiation.incrementRound();
        List<NegotiationMessage> messages = proposeForPending(negotiation);

        persist(negotiation, messages);
        publish(negotiation, NegotiationEventType.STARTED);
    }

    @Override
    public void answer(Long negotiationId, Long accountId, int roundNo, List<AnswerInput> answers) {
        Negotiation negotiation = load(negotiationId);
        PartyRole role = resolveRole(negotiation, accountId);
        ensureInProgress(negotiation);

        SenderType sender = role == PartyRole.CLIENT ? SenderType.CLIENT : SenderType.FREELANCER;
        List<NegotiationMessage> messages = new ArrayList<>();

        for (AnswerInput answer : answers) {
            NegotiationCondition condition = negotiation.findCondition(answer.conditionId());
            if (answer.accepted()) {
                String lockValue = messageRepository.findLatestProposal(negotiationId, condition.getId())
                        .map(NegotiationMessage::getProposedValue)
                        .orElse(answer.proposedValue());
                condition.lock(lockValue);
                messages.add(NegotiationMessage.response(negotiationId, condition.getId(),
                        negotiation.getTotalRound(), sender, "제안을 수락했습니다.", "YES"));
            } else {
                if (answer.proposedValue() == null || answer.proposedValue().isBlank()) {
                    throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
                }
                condition.redirect(role, answer.proposedValue());
                messages.add(NegotiationMessage.response(negotiationId, condition.getId(),
                        negotiation.getTotalRound(), sender, "제안을 거절하고 재지시했습니다.", answer.proposedValue()));
            }
        }

        if (negotiation.allConditionsAgreed()) {
            negotiation.agree(finalAmount(negotiation));
            messages.add(NegotiationMessage.system(negotiationId, negotiation.getTotalRound(),
                    "모든 조건이 합의되어 협상이 타결되었습니다."));
        } else {
            advanceOrFail(negotiation, messages);
        }

        persist(negotiation, messages);
        publish(negotiation, switch (negotiation.getStatus()) {
            case AGREED -> NegotiationEventType.AGREED;
            case FAILED -> NegotiationEventType.FAILED;
            default -> NegotiationEventType.ANSWERED;
        });
    }

    @Override
    public void giveUp(Long negotiationId, Long accountId, String reason) {
        Negotiation negotiation = load(negotiationId);
        resolveRole(negotiation, accountId);   // 당사자 검증(NG_002)

        String endReason = (reason == null || reason.isBlank()) ? "협상 포기" : reason;
        negotiation.fail(endReason);
        persist(negotiation, List.of(NegotiationMessage.system(negotiationId, negotiation.getTotalRound(),
                "협상이 종료되었습니다: " + endReason)));
        publish(negotiation, NegotiationEventType.FAILED);
    }

    // ----- helpers -----

    private void publish(Negotiation negotiation, NegotiationEventType type) {
        eventPort.publish(new NegotiationEvent(
                negotiation.getId(), type, negotiation.getStatus(), negotiation.getTotalRound()));
    }

    /** 다음 라운드로 넘기며 미합의 조건 제안 생성. 라운드 상한 소진 시 자동 결렬(설계 #5). */
    private void advanceOrFail(Negotiation negotiation, List<NegotiationMessage> messages) {
        try {
            negotiation.incrementRound();
        } catch (BusinessException e) {
            if (e.getErrorCode() == NegotiationErrorCode.ROUND_LIMIT_REACHED) {
                negotiation.fail("라운드 상한(15회) 소진으로 자동 결렬");
                messages.add(NegotiationMessage.system(negotiation.getId(), negotiation.getTotalRound(),
                        "라운드 상한(15회) 소진으로 협상이 자동 결렬되었습니다."));
                return;
            }
            throw e;
        }
        messages.addAll(proposeForPending(negotiation));
    }

    /** PENDING 조건들에 대한 제안 메시지 생성(현재 라운드). 제안값은 AI 포트(실패 시 stub 폴백)에서 온다. */
    private List<NegotiationMessage> proposeForPending(Negotiation negotiation) {
        List<NegotiationCondition> pending = negotiation.getConditions().stream()
                .filter(condition -> !condition.isAgreed())
                .toList();
        if (pending.isEmpty()) {
            return List.of();
        }

        List<NegotiationProposalPort.ConditionInput> inputs = pending.stream()
                .map(c -> new NegotiationProposalPort.ConditionInput(c.getId(), c.getConditionType(),
                        c.getClientValue(), c.getFreelancerValue(), c.getClientFloor(), c.getFreelancerFloor()))
                .toList();
        Map<Long, NegotiationProposalPort.Proposal> byId = proposalPort.propose(
                        new NegotiationProposalPort.ProposalContext(negotiation.getId(),
                                negotiation.getTotalRound(), negotiation.getBudgetCap(), inputs))
                .stream()
                .collect(Collectors.toMap(NegotiationProposalPort.Proposal::conditionId, Function.identity(),
                        (a, b) -> a));

        List<NegotiationMessage> messages = new ArrayList<>();
        for (NegotiationCondition condition : pending) {
            NegotiationProposalPort.Proposal p = byId.get(condition.getId());
            if (p == null) {
                continue;
            }
            messages.add(NegotiationMessage.proposal(negotiation.getId(), condition.getId(),
                    negotiation.getTotalRound(), SenderType.SYSTEM, p.content(), p.reason(), p.proposedValue()));
        }
        return messages;
    }

    private long finalAmount(Negotiation negotiation) {
        return negotiation.getConditions().stream()
                .filter(c -> c.getConditionType() == ConditionType.AMOUNT && c.getAgreedValue() != null)
                .map(c -> parseOrNull(c.getAgreedValue()))
                .filter(v -> v != null)
                .findFirst()
                .orElse(negotiation.getBudgetCap());
    }

    private NegotiationCondition findByType(Negotiation negotiation, ConditionType type) {
        return negotiation.getConditions().stream()
                .filter(c -> c.getConditionType() == type)
                .findFirst()
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.INVALID_CONDITION));
    }

    private PartyRole resolveRole(Negotiation negotiation, Long accountId) {
        Long clientProfileId = projectReaderPort.findById(negotiation.getProjectId())
                .map(ProjectReaderPort.ProjectView::clientProfileId).orElse(null);
        return viewerResolver.resolve(accountId, negotiation.getFreelancerId(), clientProfileId);
    }

    private Negotiation load(Long negotiationId) {
        return negotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NEGOTIATION_NOT_FOUND));
    }

    private void ensureInProgress(Negotiation negotiation) {
        if (negotiation.getStatus() != com.pairing.negotiation.domain.model.NegotiationStatus.IN_PROGRESS) {
            throw new BusinessException(NegotiationErrorCode.NOT_IN_PROGRESS);
        }
    }

    private void persist(Negotiation negotiation, List<NegotiationMessage> messages) {
        negotiationRepository.save(negotiation);
        if (!messages.isEmpty()) {
            messageRepository.saveAll(messages);
        }
    }

    private Long parseOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
