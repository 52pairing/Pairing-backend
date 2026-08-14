package com.pairing.negotiation.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 협상 애그리거트 루트. (요구사항 R06~R12)
 *
 * <p>매칭 수락으로 생성되며, 서로 맞지 않는 조건(conditions)만 담는다. 백엔드는 심판 역할로
 * 라운드 카운트/락/타결·결렬을 확정하고, 실제 제안 문구 생성은 파이썬 AI 에이전트가 맡는다.
 *
 * <p>라운드 상한 {@link #MAX_ROUND}회. 소진 시 자동 결렬(설계 #5).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Negotiation {

    /** 대리인 왕복 라운드 상한. 소진 시 자동 결렬. */
    public static final int MAX_ROUND = 15;

    private Long id;
    private Long requestId;
    private Long projectId;
    private Long positionId;
    private Long freelancerId;
    private NegotiationStatus status;
    private int totalRound;
    private Long agreedAmount;   // 합의된 월 단가(원). 타결 전 null. 계약 총액은 계약 도메인이 개월 수로 곱해 계산한다
    private Long budgetCap;      // 순예산 월 단가 상한(원). 매칭이 배정(총예산 ÷ 인원 ÷ 개월)
    private Long freelancerMonthlyPay;   // 수락 시점 프리랜서 희망 월 단가(원). AMOUNT 협상이 없을 때의 합의값 기준
    private Long floorAmount;    // [레거시] AMOUNT 조건 floor. 조건별 floor 로 대체됨
    private LocalDateTime aiOutAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String endReason;
    private LocalDateTime clientLastReadAt;       // 클라가 마지막으로 협상을 읽은 시각(안 읽은 제안 배지 기준선)
    private LocalDateTime freelancerLastReadAt;   // 프리가 마지막으로 협상을 읽은 시각
    private NegotiationAgentState agentState;     // 대리인(A2A) 실행 상태. 비동기라 저장이 필요하다
    private LocalDateTime agentStartedAt;         // RUNNING 이 된 시각. 죽은 실행(stuck) 판정 기준
    // 최종 절충 단계 진입 여부. 라운드 상한(15회)까지 합의 못 하면 true 가 되고, status 는
    // IN_PROGRESS 로 남는다(새 status 값을 만들면 RDS CHECK 제약·기존 switch 를 전부 건드려야 해서
    // 플래그로 표현한다). true 인 동안에는 최종 절충안 수락/포기만 받는다.
    private boolean finalOffer;
    private boolean clientFinalAccepted;          // 클라가 최종 절충안을 수락했는가
    private boolean freelancerFinalAccepted;      // 프리가 최종 절충안을 수락했는가
    private List<NegotiationCondition> conditions;

    private Negotiation(Long requestId, Long projectId, Long positionId, Long freelancerId,
                        Long budgetCap, Long freelancerMonthlyPay, List<NegotiationCondition> conditions) {
        validateCreation(requestId, projectId, positionId, freelancerId, budgetCap);
        this.requestId = requestId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.budgetCap = budgetCap;
        this.freelancerMonthlyPay = freelancerMonthlyPay;
        this.floorAmount = 0L;
        this.status = NegotiationStatus.IN_PROGRESS;
        this.totalRound = 0;
        this.startedAt = LocalDateTime.now();
        this.agentState = NegotiationAgentState.IDLE;
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private Negotiation(Long id, Long requestId, Long projectId, Long positionId, Long freelancerId,
                        NegotiationStatus status, int totalRound, Long agreedAmount, Long budgetCap,
                        Long freelancerMonthlyPay, Long floorAmount, LocalDateTime aiOutAt,
                        LocalDateTime startedAt, LocalDateTime endedAt, String endReason,
                        LocalDateTime clientLastReadAt, LocalDateTime freelancerLastReadAt,
                        NegotiationAgentState agentState, LocalDateTime agentStartedAt,
                        boolean finalOffer, boolean clientFinalAccepted, boolean freelancerFinalAccepted,
                        List<NegotiationCondition> conditions) {
        this.id = id;
        this.requestId = requestId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.status = status;
        this.totalRound = totalRound;
        this.agreedAmount = agreedAmount;
        this.budgetCap = budgetCap;
        this.freelancerMonthlyPay = freelancerMonthlyPay;
        this.floorAmount = floorAmount;
        this.aiOutAt = aiOutAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
        this.clientLastReadAt = clientLastReadAt;
        this.freelancerLastReadAt = freelancerLastReadAt;
        // 컬럼을 새로 붙이기 전에 만들어진 협상은 null 이다. 옛 협상은 대리인이 도는 중일 수 없다.
        this.agentState = agentState == null ? NegotiationAgentState.IDLE : agentState;
        this.agentStartedAt = agentStartedAt;
        this.finalOffer = finalOffer;
        this.clientFinalAccepted = clientFinalAccepted;
        this.freelancerFinalAccepted = freelancerFinalAccepted;
        this.conditions = conditions == null ? List.of() : conditions;
    }

    /**
     * 매칭 수락 시 협상 생성. conditions = 계산된 불일치 조건들.
     *
     * @param freelancerMonthlyPay 수락 시점 프리 희망 월 단가. AMOUNT 가 협상 대상이 아닐 때(=상한 이내라
     *                             불일치가 없을 때) 이 값이 곧 합의 금액이므로 함께 보존한다
     */
    public static Negotiation create(Long requestId, Long projectId, Long positionId, Long freelancerId,
                                     Long budgetCap, Long freelancerMonthlyPay,
                                     List<NegotiationCondition> conditions) {
        return new Negotiation(requestId, projectId, positionId, freelancerId, budgetCap,
                freelancerMonthlyPay, conditions);
    }

    public static Negotiation reconstitute(Long id, Long requestId, Long projectId, Long positionId,
                                           Long freelancerId, NegotiationStatus status, int totalRound,
                                           Long agreedAmount, Long budgetCap, Long freelancerMonthlyPay,
                                           Long floorAmount, LocalDateTime aiOutAt, LocalDateTime startedAt,
                                           LocalDateTime endedAt, String endReason,
                                           LocalDateTime clientLastReadAt, LocalDateTime freelancerLastReadAt,
                                           NegotiationAgentState agentState, LocalDateTime agentStartedAt,
                                           boolean finalOffer, boolean clientFinalAccepted,
                                           boolean freelancerFinalAccepted,
                                           List<NegotiationCondition> conditions) {
        return new Negotiation(id, requestId, projectId, positionId, freelancerId, status, totalRound,
                agreedAmount, budgetCap, freelancerMonthlyPay, floorAmount, aiOutAt, startedAt, endedAt,
                endReason, clientLastReadAt, freelancerLastReadAt, agentState, agentStartedAt,
                finalOffer, clientFinalAccepted, freelancerFinalAccepted, conditions);
    }

    /**
     * 대리인 실행을 예약한다. <b>이미 도는 중이면 {@code false}</b> 를 돌려주고 아무것도 바꾸지 않는다.
     *
     * <p>중복 실행 방지가 여기 있다. 비동기가 되면 사용자가 응답을 기다리지 않으므로 버튼을 두 번
     * 누르거나 두 당사자가 동시에 제출하는 일이 실제로 생긴다. 그대로 두면 같은 라운드에 대리인이
     * 두 번 돌아 제안이 겹치고 A2A 비용도 두 배가 된다.
     *
     * <p>{@link NegotiationAgentState#FAILED} 에서도 다시 예약할 수 있다 — 그게 재시도 경로다.
     */
    public boolean beginAgentRun() {
        ensureInProgress();
        if (this.agentState == NegotiationAgentState.RUNNING) {
            return false;
        }
        this.agentState = NegotiationAgentState.RUNNING;
        this.agentStartedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 대리인이 <b>더 이상 돌 필요가 없어졌다</b>. 예약을 지운다.
     *
     * <p>두 경우에 부른다 — 실행이 정상으로 끝났을 때, 그리고 <b>사람이 협상을 포기했을 때</b>다.
     * 포기 쪽이 중요한 이유는, 예약을 남겨 두면 뒤늦게 도착한 A2A 응답이
     * {@code isAgentRunning()} 가드를 통과해 끝난 협상을 건드리려 들기 때문이다.
     * 여기서 지워 두면 그 응답은 조용히 버려진다.
     *
     * <p>{@link #ensureInProgress} 를 부르지 않는다 — 이 호출 직전에 타결/결렬로 상태가 바뀌었을 수
     * 있는데, 그때 예외가 나면 방금 만든 제안과 계약이 통째로 롤백된다.
     */
    public void finishAgentRun() {
        this.agentState = NegotiationAgentState.IDLE;
        this.agentStartedAt = null;
    }

    /**
     * 대리인 호출이 실패했다. 라운드는 오르지 않았고 제안도 없다.
     *
     * <p>이 상태를 남기지 않으면 화면이 "협상 중"에 영원히 멈춘다 — 비동기라 예외가 사용자 요청
     * 쪽으로 가지 않기 때문이다. 같은 문제로 계약이 DRAFT 에 갇힌 적이 있다
     * ({@code ContractDraftListener} 주석 참고).
     */
    public void failAgentRun() {
        this.agentState = NegotiationAgentState.FAILED;
        this.agentStartedAt = null;
    }

    public boolean isAgentRunning() {
        return this.agentState == NegotiationAgentState.RUNNING;
    }

    /**
     * 돈다고 표시돼 있지만 실제로는 죽은 실행인가.
     *
     * <p>서버가 A2A 응답을 기다리는 도중 재배포되면 {@code RUNNING} 인 채로 남는다. 그 행을 그냥
     * 두면 {@link #beginAgentRun} 이 계속 {@code false} 를 돌려줘 <b>그 협상만 영구히 멈춘다.</b>
     * 타임아웃보다 오래된 실행은 실패로 본다.
     */
    public boolean isAgentStuck(LocalDateTime now, long timeoutSeconds) {
        return isAgentRunning()
                && agentStartedAt != null
                && agentStartedAt.plusSeconds(timeoutSeconds).isBefore(now);
    }

    /** 대리인 왕복 1라운드 소비. 상한 도달 시 소비 불가(결렬 처리로 넘긴다). */
    public void incrementRound() {
        ensureInProgress();
        if (isMaxRoundReached()) {
            throw new BusinessException(NegotiationErrorCode.ROUND_LIMIT_REACHED);
        }
        this.totalRound++;
    }

    public boolean isMaxRoundReached() {
        return this.totalRound >= MAX_ROUND;
    }

    /** 전 조건 합의 → 타결(AI Out). */
    public void agree(Long agreedAmount) {
        ensureInProgress();
        if (!allConditionsAgreed()) {
            throw new BusinessException(NegotiationErrorCode.NO_PROPOSAL_TO_RESPOND);
        }
        this.status = NegotiationStatus.AGREED;
        this.agreedAmount = agreedAmount;
        this.aiOutAt = LocalDateTime.now();
        this.endedAt = this.aiOutAt;
    }

    /**
     * 불일치 조건이 하나도 없어(양측 조건 일치) 협상 없이 즉시 타결. 생성 시점에만 호출한다.
     * 조건이 있으면 이 경로가 아니다(정상 협상 루프로 진행).
     */
    public void agreeWithoutConditions(Long agreedAmount) {
        ensureInProgress();
        if (!conditions.isEmpty()) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
        this.status = NegotiationStatus.AGREED;
        this.agreedAmount = agreedAmount;
        this.aiOutAt = LocalDateTime.now();
        this.endedAt = this.aiOutAt;
    }

    /**
     * 타결 시점 최종 합의 조건 스냅샷(정렬 고정 = 결정적 문자열). 분쟁 대비 증거로 해시체인 로그에 봉인한다.
     * 타결(AGREED) 상태에서 호출한다. 조건이 없으면 금액만 남는다(무협상 즉시 타결).
     */
    public String finalTermsSnapshot() {
        StringBuilder sb = new StringBuilder("agreedAmount=").append(agreedAmount);
        conditions.stream()
                .sorted(Comparator.comparingInt(NegotiationCondition::getSortOrder))
                .forEach(c -> sb.append('|').append(c.getConditionType()).append('=').append(c.getAgreedValue()));
        return sb.toString();
    }

    /** 이 당사자가 협상을 마지막으로 읽은 시각을 갱신한다. "확인하지 않은 새 제안 수" 배지의 기준선. */
    public void markRead(PartyRole role, LocalDateTime at) {
        if (role == PartyRole.CLIENT) {
            this.clientLastReadAt = at;
        } else {
            this.freelancerLastReadAt = at;
        }
    }

    /** 이 당사자의 마지막 읽음 시각(없으면 null = 아직 아무 것도 안 읽음). */
    public LocalDateTime lastReadAt(PartyRole role) {
        return role == PartyRole.CLIENT ? clientLastReadAt : freelancerLastReadAt;
    }

    /** 결렬(포기 / 15회 소진). */
    public void fail(String reason) {
        ensureInProgress();
        this.status = NegotiationStatus.FAILED;
        this.endReason = reason;
        this.endedAt = LocalDateTime.now();
    }

    public boolean allConditionsAgreed() {
        return !conditions.isEmpty() && conditions.stream().allMatch(NegotiationCondition::isAgreed);
    }

    /**
     * 사람이 거절해 새 마지노선을 기다리는 쟁점이 있는가.
     *
     * <p>참이면 라운드를 진행하지 않는다. 거절은 "이 선으로는 안 된다"는 뜻일 뿐 새 선이 아니라,
     * 그대로 대리인을 다시 돌리면 같은 마지노선으로 같은 대화를 반복하며 라운드만 태운다.
     */
    public boolean awaitingRedirect() {
        return conditions.stream().anyMatch(NegotiationCondition::isRejected);
    }

    /** 아직 합의되지 않은(락 안 된) 쟁점들. 최종 절충값은 이들에만 계산·수락된다. */
    public List<NegotiationCondition> pendingConditions() {
        return conditions.stream().filter(c -> !c.isAgreed()).toList();
    }

    /**
     * 최종 절충 단계로 진입한다. status 는 IN_PROGRESS 로 유지하고 {@code finalOffer} 플래그만 세운다.
     *
     * <p>진입 후에는 일반 응답/재지시가 아니라 최종 절충안 수락({@link #acceptFinalOffer})/포기만 받는다.
     * 절충값 계산·부착은 애플리케이션 계층이 {@link NegotiationCondition#proposeCompromise} 로 한다.
     */
    public void enterFinalOffer() {
        ensureInProgress();
        this.finalOffer = true;
        // 재진입 시 이전 수락 표시가 남지 않도록 초기화(안전장치).
        this.clientFinalAccepted = false;
        this.freelancerFinalAccepted = false;
    }

    /**
     * 이 당사자가 최종 절충안을 수락한다. 양측이 모두 수락해야 타결되므로 여기서는 표시만 한다.
     * 실제 락·타결은 {@link #bothAcceptedFinalOffer} 를 확인한 애플리케이션 계층이 한다.
     */
    public void acceptFinalOffer(PartyRole role) {
        ensureInProgress();
        if (!finalOffer) {
            throw new BusinessException(NegotiationErrorCode.NOT_FINAL_OFFER);
        }
        if (role == PartyRole.CLIENT) {
            this.clientFinalAccepted = true;
        } else {
            this.freelancerFinalAccepted = true;
        }
    }

    /** 최종 절충안을 양측이 모두 수락했는가. 참이면 절충값을 락하고 타결한다. */
    public boolean bothAcceptedFinalOffer() {
        return finalOffer && clientFinalAccepted && freelancerFinalAccepted;
    }

    /** 이 당사자가 최종 절충안을 이미 수락했는가(응답 노출용). */
    public boolean hasAcceptedFinalOffer(PartyRole role) {
        return role == PartyRole.CLIENT ? clientFinalAccepted : freelancerFinalAccepted;
    }

    /** 이 당사자가 모든 쟁점에 마지노선을 냈는가. */
    public boolean hasFloorsFrom(PartyRole role) {
        return !conditions.isEmpty() && conditions.stream().allMatch(c -> c.hasFloorFrom(role));
    }

    /**
     * 양측이 마지노선을 모두 제출했는가. <b>이게 참이 되어야 대리인 협상을 시작한다.</b>
     *
     * <p>한쪽 마지노선만으로 돌리면 선을 안 그은 쪽 대리인이 지킬 게 없어 그대로 양보해 버린다.
     * 그러면 상대는 의사를 한 번도 밝히지 않았는데 계약 조건이 확정된다 — 먼저 누른 쪽이 이기는
     * 협상이 되므로, 두 번째 제출이 들어올 때까지 기다린다.
     */
    public boolean bothFloorsSubmitted() {
        return hasFloorsFrom(PartyRole.CLIENT) && hasFloorsFrom(PartyRole.FREELANCER);
    }

    public NegotiationCondition findCondition(Long conditionId) {
        return conditions.stream()
                .filter(c -> Objects.equals(c.getId(), conditionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.INVALID_CONDITION));
    }

    private void ensureInProgress() {
        if (this.status != NegotiationStatus.IN_PROGRESS) {
            throw new BusinessException(NegotiationErrorCode.NOT_IN_PROGRESS);
        }
    }

    private void validateCreation(Long requestId, Long projectId, Long positionId, Long freelancerId, Long budgetCap) {
        if (requestId == null || projectId == null || positionId == null || freelancerId == null) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
        if (budgetCap == null || budgetCap <= 0) {
            throw new BusinessException(NegotiationErrorCode.FLOOR_EXCEEDS_BUDGET);
        }
    }
}
