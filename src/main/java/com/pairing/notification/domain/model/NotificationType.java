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
    INQUIRY_ANSWERED("1:1 문의 답변"),

    /**
     * 모집 기간 만료로 프로젝트가 취소됨. (정책 P46) 프로젝트 도메인이 발행한다.
     *
     * <p>값을 추가할 때는 {@code notification} 테이블의 {@code notification_type_check} 를
     * 먼저 열어야 한다. 이 컬럼이 {@code @Enumerated(STRING)} 이라 Hibernate 가 테이블을
     * 만들 때 CHECK 를 굽는데, {@code ddl-auto: update} 는 제약을 갱신하지 않는다. 그대로
     * 두면 INSERT 가 거부되고 <b>알림을 발행한 트랜잭션이 통째로 롤백된다.</b>
     */
    PROJECT_CANCELED("프로젝트 취소");

    private final String label;
}
