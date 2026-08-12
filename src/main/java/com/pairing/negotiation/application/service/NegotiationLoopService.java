package com.pairing.negotiation.application.service;

import com.pairing.contract.application.usecase.ContractCreationUseCase;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.usecase.MatchingNegotiationOutcomeUseCase;
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
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.domain.service.NegotiationAgreedValueNormalizer;
import com.pairing.negotiation.domain.service.NegotiationFloorGuard;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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
    // 협상 결과(타결/결렬)를 매칭 요청 건에 반영하는 인바운드 포트(방향: negotiation → matching).
    private final MatchingNegotiationOutcomeUseCase matchingOutcomeUseCase;
    // 타결 시 표준계약서를 생성하는 인바운드 포트(방향: negotiation → contract).
    private final ContractCreationUseCase contractCreationUseCase;

    /**
     * 마지노선 제출. <b>양측이 모두 낸 뒤에야</b> 대리인 협상(라운드 1)이 시작된다.
     *
     * <p>먼저 낸 쪽은 저장만 하고 상대를 기다린다. 한쪽 마지노선만으로 돌리면 선을 안 그은 쪽
     * 대리인이 지킬 게 없어 그대로 양보하고, 상대는 동의한 적도 없는데 조건이 확정된다.
     */
    @Override
    public void start(Long negotiationId, Long accountId, List<FloorInput> floors) {
        Negotiation negotiation = load(negotiationId);
        PartyRole role = resolveRole(negotiation, accountId);
        ensureInProgress(negotiation);
        if (negotiation.hasFloorsFrom(role)) {
            throw new BusinessException(NegotiationErrorCode.FLOOR_ALREADY_SUBMITTED);
        }

        // 요청자 본인 쪽 마지노선 저장(쟁점별). 저장 전에 계약 표기로 정규화한다 —
        // 화면이 "4"(단위 없음)나 "재택"(코드 아닌 라벨)을 보내면 대리인이 비교조차 못 한다.
        for (FloorInput floor : floors) {
            NegotiationCondition condition = findByType(negotiation, floor.conditionType());
            condition.submitFloor(role, normalizeFloor(condition, floor.value()));
        }

        // 상대가 아직 안 냈으면 여기서 멈춘다. 상대 화면에는 '내 응답 필요'로 뜬다.
        if (!negotiation.bothFloorsSubmitted()) {
            persist(negotiation, List.of(NegotiationMessage.system(negotiationId, negotiation.getTotalRound(),
                    "마지노선이 저장되었습니다. 상대방이 조건을 입력하면 AI 대리인 협상이 시작됩니다.")));
            publish(negotiation, NegotiationEventType.STARTED);
            return;
        }

        // 양측 마지노선이 모두 모였다 → 초기 제안(라운드 1) 생성.
        negotiation.incrementRound();
        List<NegotiationMessage> messages = new ArrayList<>(proposeForPending(negotiation));
        // 첫 라운드에서 대리인끼리 전 조건을 합의해 버릴 수 있다. 그때 바로 타결시킨다.
        settleIfAllAgreed(negotiation, messages);

        persist(negotiation, messages);
        createContractIfAgreed(negotiation);
        publish(negotiation, negotiation.getStatus() == NegotiationStatus.AGREED
                ? NegotiationEventType.AGREED : NegotiationEventType.STARTED);
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
                // 수락 대상은 '상대가 낸 제안'이다. 내 편 대리인이 부른 값을 내가 수락하면 상대는
                // 동의한 적 없는 조건이 확정된다(먼저 누른 쪽이 이기는 협상).
                String lockValue = messageRepository
                        .findLatestProposalExcluding(negotiationId, condition.getId(), role.ownSenders())
                        .map(NegotiationMessage::getProposedValue)
                        .orElseThrow(() -> new BusinessException(NegotiationErrorCode.NO_PROPOSAL_TO_RESPOND));
                // 사람도 클릭 한 번으로 자기가 그은 선을 넘지 못한다. 대리인에게 적용하는 기준과 같다.
                // 양보하려면 [거절] → 재지시로 마지노선을 다시 그어야 한다(그 경로가 이미 있다).
                if (!NegotiationFloorGuard.respectsFloors(condition.getConditionType(), lockValue,
                        condition.getClientFloor(), condition.getFreelancerFloor())) {
                    throw new BusinessException(NegotiationErrorCode.ACCEPT_BREAKS_FLOOR);
                }
                condition.lock(lockValue);
                messages.add(NegotiationMessage.response(negotiationId, condition.getId(),
                        negotiation.getTotalRound(), sender, "제안을 수락했습니다.", "YES", accountId));
            } else if (answer.proposedValue() == null || answer.proposedValue().isBlank()) {
                // 1단계 — 거절만 표시한다. 화면은 이때 재지시 입력(새 마지노선)을 띄운다.
                condition.reject();
                messages.add(NegotiationMessage.response(negotiationId, condition.getId(),
                        negotiation.getTotalRound(), sender, "제안을 거절했습니다.", "NO", accountId));
            } else {
                // 2단계 — 새 마지노선을 받아 재협상으로 되돌린다. 값은 /start 와 같은 규칙으로 정규화한다.
                String normalized = normalizeFloor(condition, answer.proposedValue());
                condition.redirect(role, normalized);
                messages.add(NegotiationMessage.response(negotiationId, condition.getId(),
                        negotiation.getTotalRound(), sender, "제안을 거절하고 재지시했습니다.",
                        normalized, accountId));
            }
        }

        if (!settleIfAllAgreed(negotiation, messages) && !negotiation.awaitingRedirect()) {
            advanceOrFail(negotiation, messages);
        }
        // 거절만 들어온 경우(awaitingRedirect)는 여기서 멈춘다. 사람이 새 마지노선을 낼 때까지
        // 라운드를 태우지 않는다 — 다음 요청의 재지시가 들어오면 그때 라운드가 오른다.

        persist(negotiation, messages);
        createContractIfAgreed(negotiation);

        // 사람 채팅방은 타결이 아니라 계약 체결 시 열린다(계약 도메인이 ChatActivationUseCase 로 호출).
        publish(negotiation, switch (negotiation.getStatus()) {
            case AGREED -> NegotiationEventType.AGREED;
            case FAILED -> NegotiationEventType.FAILED;
            default -> NegotiationEventType.ANSWERED;
        });
    }

    /**
     * 내 마지노선만 다시 긋는다. 라운드도 안 올리고 대리인도 안 돌린다.
     *
     * <p>이미 합의된 쟁점은 못 고친다. 락된 값과 모순되는 선이 남으면 이후 판정이 전부 흔들린다.
     */
    @Override
    public void updateFloors(Long negotiationId, Long accountId, List<FloorInput> floors) {
        Negotiation negotiation = load(negotiationId);
        PartyRole role = resolveRole(negotiation, accountId);
        ensureInProgress(negotiation);

        for (FloorInput floor : floors) {
            NegotiationCondition condition = findByType(negotiation, floor.conditionType());
            if (condition.isAgreed()) {
                throw new BusinessException(NegotiationErrorCode.CONDITION_ALREADY_LOCKED);
            }
            condition.submitFloor(role, normalizeFloor(condition, floor.value()));
        }
        negotiationRepository.save(negotiation);
    }

    @Override
    public void giveUp(Long negotiationId, Long accountId, String reason) {
        Negotiation negotiation = load(negotiationId);
        resolveRole(negotiation, accountId);   // 당사자 검증(NG_002)

        String endReason = (reason == null || reason.isBlank()) ? "협상 포기" : reason;
        negotiation.fail(endReason);
        // 결렬(협상 포기) → 매칭 요청을 협상 결렬(NEGOTIATION_FAILED)로 종결(같은 트랜잭션).
        matchingOutcomeUseCase.markNegotiationFailed(negotiation.getRequestId());
        persist(negotiation, List.of(NegotiationMessage.system(negotiationId, negotiation.getTotalRound(),
                "협상이 종료되었습니다: " + endReason)));
        publish(negotiation, NegotiationEventType.FAILED);
    }

    @Override
    public void markRead(Long negotiationId, Long accountId) {
        Negotiation negotiation = load(negotiationId);
        PartyRole role = resolveRole(negotiation, accountId);   // 당사자 검증(NG_002) 포함
        negotiation.markRead(role, LocalDateTime.now());
        negotiationRepository.save(negotiation);
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
                // 자동 결렬 → 매칭 요청을 협상 결렬(NEGOTIATION_FAILED)로 종결(같은 트랜잭션).
                matchingOutcomeUseCase.markNegotiationFailed(negotiation.getRequestId());
                messages.add(NegotiationMessage.system(negotiation.getId(), negotiation.getTotalRound(),
                        "라운드 상한(15회) 소진으로 협상이 자동 결렬되었습니다."));
                return;
            }
            throw e;
        }
        messages.addAll(proposeForPending(negotiation));
        // 이 라운드에서 대리인끼리 남은 조건을 전부 합의했을 수 있다. 그러면 여기서 타결이다.
        settleIfAllAgreed(negotiation, messages);
    }

    /**
     * 모든 쟁점이 합의됐으면 타결 처리하고 {@code true} 를 돌려준다.
     *
     * <p><b>사람 응답 경로에만 두면 안 된다.</b> 대리인끼리 마지막 조건까지 자동 합의하는 경우가 있고,
     * 그때 이 판정이 없으면 조건은 전부 🔒 인데 협상은 IN_PROGRESS 에 갇힌다. 계약서도 안 생기고
     * 매칭 요청도 CONTRACT_PENDING 으로 넘어가지 않는다(실제로 그 상태를 확인했다).
     */
    private boolean settleIfAllAgreed(Negotiation negotiation, List<NegotiationMessage> messages) {
        if (!negotiation.allConditionsAgreed()) {
            return false;
        }
        negotiation.agree(finalAmount(negotiation));
        // 타결 → 매칭 요청을 계약 대기(CONTRACT_PENDING)로 전환(같은 트랜잭션).
        matchingOutcomeUseCase.markNegotiationAgreed(negotiation.getRequestId());
        // 타결 시점 최종 조건을 해시체인 로그에 봉인한다(분쟁 대비 증거).
        // audit 이라 당사자 화면에는 안 보인다 — 사람에겐 화면의 "모든 조건에 합의했습니다" 카드가
        // 같은 내용을 이미 보여 준다. 이 줄은 기계가 파싱할 증거다.
        messages.add(NegotiationMessage.audit(negotiation.getId(), negotiation.getTotalRound(),
                "최종 조건 봉인: " + negotiation.finalTermsSnapshot()));
        return true;
    }

    /**
     * 타결이면 표준계약서를 만든다(요구사항 44행).
     *
     * <p>반드시 {@code persist} 뒤에 불러야 한다 — 계약 쪽이 {@code getAgreedForContract} 로 협상을
     * 다시 읽으므로 저장 전에 부르면 못 찾는다. 같은 트랜잭션이라 계약 생성이 실패하면 타결도 함께
     * 롤백된다(멱등이라 재시도 안전).
     */
    private void createContractIfAgreed(Negotiation negotiation) {
        if (negotiation.getStatus() == NegotiationStatus.AGREED) {
            contractCreationUseCase.createFromNegotiation(negotiation.getId());
        }
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
        NegotiationProposalPort.A2AResult result = proposalPort.propose(
                new NegotiationProposalPort.ProposalContext(negotiation.getId(),
                        negotiation.getTotalRound(), negotiation.getBudgetCap(), inputs));

        // 두 대리인의 대화를 로그로 기록한다(발신자 = CLIENT_AGENT/FREELANCER_AGENT).
        int round = negotiation.getTotalRound();
        List<NegotiationMessage> messages = new ArrayList<>();
        for (NegotiationProposalPort.AgentMessage m : result.messages()) {
            messages.add(NegotiationMessage.proposal(negotiation.getId(), m.conditionId(), round,
                    senderOf(m.sender()), m.content(), m.reason(), m.proposedValue()));
        }

        // 대리인끼리 합의한 조건은 자동 락(사람은 승인 패널에서 [이미 합의🔒]로 본다).
        Map<Long, NegotiationCondition> pendingById = pending.stream()
                .collect(Collectors.toMap(NegotiationCondition::getId, Function.identity(), (a, b) -> a));
        for (NegotiationProposalPort.ConditionOutcome o : result.outcomes()) {
            NegotiationCondition condition = pendingById.get(o.conditionId());
            if (condition == null || !o.agreed() || condition.isAgreed()) {
                continue;
            }
            // 대리인이 사람이 그은 선을 넘겨 합의했으면 락하지 않는다. 미합의로 남겨 승인 패널로 넘긴다.
            // (프롬프트로 유도하지만 LLM 이 지킨다는 보장이 없어 여기서 최종 확인한다)
            if (!NegotiationFloorGuard.respectsFloors(condition.getConditionType(), o.proposedValue(),
                    condition.getClientFloor(), condition.getFreelancerFloor())) {
                log.warn("마지노선을 넘은 합의라 락하지 않음(사람 승인으로 이관): negotiationId={}, conditionId={}, "
                                + "type={}, value={}", negotiation.getId(), condition.getId(),
                        condition.getConditionType(), o.proposedValue());
                continue;
            }
            condition.lock(o.proposedValue());
        }
        return messages;
    }

    /** 파이썬이 준 sender 문자열 → SenderType. 알 수 없으면 SYSTEM 으로 방어. */
    private SenderType senderOf(String sender) {
        try {
            return SenderType.valueOf(sender);
        } catch (IllegalArgumentException | NullPointerException e) {
            return SenderType.SYSTEM;
        }
    }

    /**
     * 합의된 <b>월 단가</b>(원). AMOUNT 가 협상 대상이었으면 그 합의값, 아니면 수락 시점 프리 희망 단가다.
     *
     * <p>예산 상한(budgetCap)으로 떨어뜨리지 않는다. AMOUNT 조건이 없다는 건 프리 단가가 상한 이내라
     * 다툴 게 없었다는 뜻이라, 상한을 합의값으로 쓰면 아무도 제시한 적 없는 금액이 계약서에 찍힌다.
     */
    private long finalAmount(Negotiation negotiation) {
        return negotiation.getConditions().stream()
                .filter(c -> c.getConditionType() == ConditionType.AMOUNT && c.getAgreedValue() != null)
                .map(c -> parseOrNull(c.getAgreedValue()))
                .filter(v -> v != null)
                .findFirst()
                .orElseGet(() -> negotiation.getFreelancerMonthlyPay() != null
                        ? negotiation.getFreelancerMonthlyPay()
                        : negotiation.getBudgetCap());   // 구 데이터(월단가 미보존) 방어
    }

    private NegotiationCondition findByType(Negotiation negotiation, ConditionType type) {
        return negotiation.getConditions().stream()
                .filter(c -> c.getConditionType() == type)
                .findFirst()
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.INVALID_CONDITION));
    }

    /** PERIOD 처럼 단위가 빠졌을 때 복원 기준이 되는 기존 값(희망값). */
    private String reference(NegotiationCondition condition) {
        return condition.getClientValue() != null ? condition.getClientValue() : condition.getFreelancerValue();
    }

    /**
     * 마지노선을 계약 표기로 정규화한다. 해석 불가하면 NG_004 로 거부한다.
     *
     * <p>최초 제출(/start)과 재지시(/answers)가 같은 규칙을 써야 한다. 한쪽만 정규화하면
     * 재지시로 들어온 {@code "재택"} 같은 값이 대리인 비교에서 그대로 깨진다.
     */
    private String normalizeFloor(NegotiationCondition condition, String value) {
        return NegotiationAgreedValueNormalizer
                .normalize(condition.getConditionType(), value, reference(condition))
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
        if (messages.isEmpty()) {
            return;
        }
        // 해시 체인 봉인: 직전 로그 해시부터 이어 붙인다(append 순서 = id 순서와 일치).
        String prevHash = messageRepository.findLatestHash(negotiation.getId())
                .orElse(NegotiationMessage.GENESIS_HASH);
        for (NegotiationMessage message : messages) {
            message.seal(prevHash);
            prevHash = message.getContentHash();
        }
        messageRepository.saveAll(messages);
    }

    private Long parseOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
