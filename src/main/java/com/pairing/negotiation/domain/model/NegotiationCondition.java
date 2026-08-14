package com.pairing.negotiation.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 협상 조건 하나. 서로 맞지 않아 협상 대상이 된 쟁점을 나타낸다.
 *
 * <p>값의 성격 구분(설계 #1):
 * <ul>
 *   <li>{@code clientValue}/{@code freelancerValue} = 각 측 희망값(공개·고정). 초기 제안 카드·상대 희망 힌트용.</li>
 *   <li>{@code clientFloor}/{@code freelancerFloor} = 각 측 마지노선(비공개). 응답에는 뷰어 본인 것만 내려간다.</li>
 * </ul>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationCondition {

    private Long id;
    private Long negotiationId;
    private ConditionType conditionType;
    private String clientValue;       // 희망값(공개·고정)
    private String freelancerValue;   // 희망값(공개·고정)
    private String clientFloor;       // 마지노선(비공개)
    private String freelancerFloor;   // 마지노선(비공개)
    private String agreedValue;
    private ConditionStatus status;
    private int roundCount;
    private int sortOrder;
    private LocalDateTime agreedAt;
    // 최종 절충안(양쪽이 마지노선을 넘겨 만나는 중간값). 최종 절충 진입 시 미합의 조건에만 채워지고,
    // 양측이 수락하면 이 값이 agreedValue 로 락된다. 그 외에는 null(합의됐거나 절충 불가).
    private String compromiseValue;

    private NegotiationCondition(ConditionType conditionType, String clientValue, String freelancerValue, int sortOrder) {
        if (conditionType == null) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
        this.conditionType = conditionType;
        this.clientValue = clientValue;
        this.freelancerValue = freelancerValue;
        this.sortOrder = sortOrder;
        this.status = ConditionStatus.PENDING;
        this.roundCount = 0;
    }

    private NegotiationCondition(Long id, Long negotiationId, ConditionType conditionType,
                                String clientValue, String freelancerValue,
                                String clientFloor, String freelancerFloor,
                                String agreedValue, ConditionStatus status,
                                int roundCount, int sortOrder, LocalDateTime agreedAt,
                                String compromiseValue) {
        this.id = id;
        this.negotiationId = negotiationId;
        this.conditionType = conditionType;
        this.clientValue = clientValue;
        this.freelancerValue = freelancerValue;
        this.clientFloor = clientFloor;
        this.freelancerFloor = freelancerFloor;
        this.agreedValue = agreedValue;
        this.status = status;
        this.roundCount = roundCount;
        this.sortOrder = sortOrder;
        this.agreedAt = agreedAt;
        this.compromiseValue = compromiseValue;
    }

    /** 협상 생성 시점의 불일치 조건 생성. 희망값만 채우고 마지노선은 이후 입력받는다. */
    public static NegotiationCondition create(ConditionType conditionType, String clientValue,
                                              String freelancerValue, int sortOrder) {
        return new NegotiationCondition(conditionType, clientValue, freelancerValue, sortOrder);
    }

    public static NegotiationCondition reconstitute(Long id, Long negotiationId, ConditionType conditionType,
                                                    String clientValue, String freelancerValue,
                                                    String clientFloor, String freelancerFloor,
                                                    String agreedValue, ConditionStatus status,
                                                    int roundCount, int sortOrder, LocalDateTime agreedAt,
                                                    String compromiseValue) {
        return new NegotiationCondition(id, negotiationId, conditionType, clientValue, freelancerValue,
                clientFloor, freelancerFloor, agreedValue, status, roundCount, sortOrder, agreedAt,
                compromiseValue);
    }

    /** 마지노선 입력/재조정. 요청자 role 쪽 floor 만 갱신한다(상대 것은 건드리지 않는다). */
    public void submitFloor(PartyRole role, String floorValue) {
        if (role == PartyRole.CLIENT) {
            this.clientFloor = floorValue;
        } else {
            this.freelancerFloor = floorValue;
        }
    }

    /**
     * 최종 절충값을 붙인다(최종 절충 진입 시, 미합의 조건에만). 이미 합의된 조건은 절충 대상이 아니다.
     *
     * <p>값을 붙일 뿐 락하지는 않는다 — 양측이 모두 수락해야 {@link #lockCompromise} 로 확정된다.
     */
    public void proposeCompromise(String value) {
        if (this.status == ConditionStatus.AGREED) {
            throw new BusinessException(NegotiationErrorCode.CONDITION_ALREADY_LOCKED);
        }
        this.compromiseValue = value;
    }

    /**
     * 양측이 최종 절충안을 수락 → 절충값을 합의값으로 락한다.
     *
     * <p>절충값은 사람이 자기 마지노선을 넘겨 받아들인 값이라 {@link NegotiationFloorGuard} 하한
     * 검증을 거치지 않는다({@code acceptBelowFloor} 와 같은 정책). 대신 <b>양측이 모두 명시적으로
     * 수락</b>했을 때만 이 경로가 열린다.
     */
    public void lockCompromise() {
        if (this.compromiseValue == null || this.compromiseValue.isBlank()) {
            throw new BusinessException(NegotiationErrorCode.NO_PROPOSAL_TO_RESPOND);
        }
        lock(this.compromiseValue);
    }

    /** 사람이 수락 → 합의값 확정, 락. */
    public void lock(String value) {
        if (this.status == ConditionStatus.AGREED) {
            throw new BusinessException(NegotiationErrorCode.CONDITION_ALREADY_LOCKED);
        }
        this.status = ConditionStatus.AGREED;
        this.agreedValue = value;
        this.agreedAt = LocalDateTime.now();
    }

    /** 사람이 거절 → 재협상 대기(REJECTED). 재지시로 마지노선 다시 받는다. */
    public void reject() {
        if (this.status == ConditionStatus.AGREED) {
            throw new BusinessException(NegotiationErrorCode.CONDITION_ALREADY_LOCKED);
        }
        this.status = ConditionStatus.REJECTED;
    }

    /**
     * 재지시: 거절 조건의 마지노선을 다시 받아 재협상으로 되돌린다.
     *
     * <p>여기서 라운드 수를 올리지 않는다. 재지시는 "다시 붙어 보라"는 지시일 뿐 아직 오간 말이
     * 없고, 실제 논의는 다음 라운드에 대리인이 제안을 내면서 일어난다. 양쪽에서 올리면
     * 재지시 한 번이 두 라운드로 세진다.
     */
    public void redirect(PartyRole role, String newFloor) {
        submitFloor(role, newFloor);
        this.status = ConditionStatus.PENDING;
    }

    /**
     * 이 쟁점이 한 라운드 더 논의됐다.
     *
     * <p>협상 전체 라운드({@code Negotiation.totalRound})와 <b>다르다.</b> 먼저 합의된 쟁점은 이후
     * 라운드에서 빠지므로 쟁점마다 값이 갈린다 — 화면의 "이 조건은 몇 번 오갔나"가 이 값이다.
     */
    public void countRound() {
        this.roundCount++;
    }

    public boolean isAgreed() {
        return this.status == ConditionStatus.AGREED;
    }

    /** 사람이 거절해 새 마지노선을 기다리는 중인가. 이 상태에서는 대리인을 다시 돌리지 않는다. */
    public boolean isRejected() {
        return this.status == ConditionStatus.REJECTED;
    }

    /** 이 쟁점에 해당 측 마지노선이 들어와 있는가. 양측이 다 내야 대리인 협상을 시작한다. */
    public boolean hasFloorFrom(PartyRole role) {
        String floor = role == PartyRole.CLIENT ? this.clientFloor : this.freelancerFloor;
        return floor != null && !floor.isBlank();
    }

    /** 뷰어 본인 마지노선만 노출(상대 것은 절대 반환하지 않는다). */
    public String floorForViewer(PartyRole viewer) {
        return viewer == PartyRole.CLIENT ? this.clientFloor : this.freelancerFloor;
    }
}
