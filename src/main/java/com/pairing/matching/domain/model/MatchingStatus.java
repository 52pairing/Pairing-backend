package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

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

    /**
     * 포지션 자리를 다시 비워주는 종결 상태. 인원 계산에서 빼야 그 자리를 다시 채울 수 있다
     * (명세의 "2/3명 진행 중 · 1명 계약 종료" 화면이 이 경우다).
     *
     * <p>정상 완료(CLOSED)는 자리를 비우지 않는다 — 그 인원은 이미 뽑아서 일까지 끝낸 것이라
     * 다시 뽑을 대상이 아니다.
     */
    public static final List<MatchingStatus> SLOT_RELEASED = List.of(REJECTED, NEGOTIATION_FAILED, TERMINATED);

    private final String label;
}
