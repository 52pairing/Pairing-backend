package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.domain.model.ConditionType;

import java.util.List;

/**
 * 협상 진행 인바운드 포트(사람 액션). 생성(create)과 별개다.
 *
 * <ul>
 *   <li>{@link #start} 마지노선 설정 + 초기 제안 생성(라운드 1)</li>
 *   <li>{@link #answer} 조건별 수락(락)/거절(재지시) + 다음 제안 or 타결 or 자동 결렬</li>
 *   <li>{@link #giveUp} 즉시 결렬</li>
 * </ul>
 * 제안 생성은 현재 stub. 파이썬 AI 연동은 후속 슬라이스에서 교체한다.
 */
public interface NegotiationLoopUseCase {

    void start(Long negotiationId, Long accountId, List<FloorInput> floors);

    void answer(Long negotiationId, Long accountId, int roundNo, List<AnswerInput> answers);

    /**
     * 협상 중 <b>내 마지노선만</b> 다시 긋는다. 라운드를 올리지도, 대리인을 돌리지도 않는다.
     *
     * <p>상대 제안이 내가 그은 선 밖이면 수락이 막히는데({@code NG_011}), 선을 고칠 길이 없으면
     * 그 안내가 막다른 길이 된다. 양보하려는 사람이 스스로 선을 넓힐 수 있어야 한다.
     *
     * <p>조정만으로는 협상이 한 발도 안 나간다 — 대리인은 상대가 재지시할 때 돈다. 그래서
     * 라운드 상한(15회)을 우회하는 데 쓸 수 없다.
     */
    void updateFloors(Long negotiationId, Long accountId, List<FloorInput> floors);

    void giveUp(Long negotiationId, Long accountId, String reason);

    /**
     * 협상 읽음 처리. 요청자 본인 쪽의 "마지막 읽은 시각"을 현재로 갱신한다.
     * "확인하지 않은 새 제안 수" 배지의 기준선이 된다(채팅 읽음 처리와 같은 패턴).
     */
    void markRead(Long negotiationId, Long accountId);

    /** 쟁점별 마지노선(요청자 본인 것). */
    record FloorInput(ConditionType conditionType, String value) {
    }

    /**
     * 조건 1건 응답. accepted=false 면 proposedValue(새 마지노선/역제안) 필요.
     *
     * <p>{@code acceptBelowFloor} 는 사람이 <b>자기 마지노선을 넘겨서라도 이 제안을 직접 수락</b>하겠다는
     * 명시적 신호다(accepted=true 일 때만 의미). 등록 최소가/마지노선은 대리인의 하한이지 사람의
     * 명시적 수락까지 막는 천장은 아니라는 정책. 상대 마지노선은 이 플래그와 무관하게 항상 지킨다.
     */
    record AnswerInput(Long conditionId, boolean accepted, String proposedValue, boolean acceptBelowFloor) {

        /** 하위호환: {@code acceptBelowFloor} 는 기본 false. */
        public AnswerInput(Long conditionId, boolean accepted, String proposedValue) {
            this(conditionId, accepted, proposedValue, false);
        }
    }
}
