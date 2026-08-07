package com.pairing.notification.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 종류. (요구사항 R27)
 *
 * <p>알림을 누르면 해당 화면으로 이동해야 하므로, 종류마다 이동 대상이 정해져 있다.
 * 이동 경로는 서버가 linkUrl 로 내려준다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    MATCHING_RECOMMENDED("추천 완료"),
    MATCHING_REQUESTED("매칭 요청 도착"),
    MATCHING_ACCEPTED("매칭 요청 수락"),
    MATCHING_REJECTED("매칭 요청 거절"),
    NEGOTIATION_STARTED("협상 시작"),
    NEGOTIATION_PROPOSED("새로운 AI 제안"),
    NEGOTIATION_FAILED("협상 결렬"),
    CONTRACT_CREATED("계약서 생성"),
    CONTRACT_SIGNED("계약서 서명"),
    CONTRACT_REJECTED("계약서 거절"),
    SETTLEMENT_DUE("수수료 결제 안내"),
    INQUIRY_ANSWERED("1:1 문의 답변");

    private final String label;
}
