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

    void giveUp(Long negotiationId, Long accountId, String reason);

    /**
     * 협상 읽음 처리. 요청자 본인 쪽의 "마지막 읽은 시각"을 현재로 갱신한다.
     * "확인하지 않은 새 제안 수" 배지의 기준선이 된다(채팅 읽음 처리와 같은 패턴).
     */
    void markRead(Long negotiationId, Long accountId);

    /** 쟁점별 마지노선(요청자 본인 것). */
    record FloorInput(ConditionType conditionType, String value) {
    }

    /** 조건 1건 응답. accepted=false 면 proposedValue(새 마지노선/역제안) 필요. */
    record AnswerInput(Long conditionId, boolean accepted, String proposedValue) {
    }
}
