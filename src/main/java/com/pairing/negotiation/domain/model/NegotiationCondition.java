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
                                int roundCount, int sortOrder, LocalDateTime agreedAt) {
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
                                                    int roundCount, int sortOrder, LocalDateTime agreedAt) {
        return new NegotiationCondition(id, negotiationId, conditionType, clientValue, freelancerValue,
                clientFloor, freelancerFloor, agreedValue, status, roundCount, sortOrder, agreedAt);
    }

    /** 마지노선 입력/재조정. 요청자 role 쪽 floor 만 갱신한다(상대 것은 건드리지 않는다). */
    public void submitFloor(PartyRole role, String floorValue) {
        if (role == PartyRole.CLIENT) {
            this.clientFloor = floorValue;
        } else {
            this.freelancerFloor = floorValue;
        }
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
