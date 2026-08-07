package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인원별(매칭 요청 1건) 상태. (요구사항 R19)
 *
 * <p>상태는 프리랜서 개인이 아니라 매칭 요청 건에 붙는다. 같은 프리랜서가 여러 프로젝트에 걸쳐 있을 수 있다.
 * REJECTED / NEGOTIATION_FAILED / TERMINATED / CLOSED 는 종결 상태이며 되돌아가지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum MatchingStatus {

    REQUEST_PENDING("요청 대기"),
    REJECTED("거절"),
    ACCEPTED("수락"),
    NEGOTIATING("협상중"),
    NEGOTIATION_FAILED("협상 결렬"),
    CONTRACT_PENDING("계약 대기"),
    CONTRACTED("계약 완료"),
    IN_PROGRESS("진행중"),
    COMPLETION_PENDING("완료 대기"),
    CLOSED("종료"),
    TERMINATED("중도 종료");

    private final String label;
}
