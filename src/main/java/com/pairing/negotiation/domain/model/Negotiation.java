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
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    private Negotiation(Long id, Long requestId, Long projectId, Long positionId, Long freelancerId,
                        NegotiationStatus status, int totalRound, Long agreedAmount, Long budgetCap,
                        Long freelancerMonthlyPay, Long floorAmount, LocalDateTime aiOutAt,
                        LocalDateTime startedAt, LocalDateTime endedAt, String endReason,
                        LocalDateTime clientLastReadAt, LocalDateTime freelancerLastReadAt,
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
                                           List<NegotiationCondition> conditions) {
        return new Negotiation(id, requestId, projectId, positionId, freelancerId, status, totalRound,
                agreedAmount, budgetCap, freelancerMonthlyPay, floorAmount, aiOutAt, startedAt, endedAt,
                endReason, clientLastReadAt, freelancerLastReadAt, conditions);
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
