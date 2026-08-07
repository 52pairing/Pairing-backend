package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 프리랜서 "프로젝트 제안" 목록 탭.
 *
 * <p>클라이언트가 보낸 매칭 요청을 프리랜서 쪽에서 보는 화면이다. 탭 하나가 여러 상태를 묶는다.
 * ALL 은 필터를 걸지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum MatchingRequestTab {

    ALL("전체", List.of()),
    REVIEWING("검토 중", List.of(MatchingStatus.REQUEST_PENDING)),
    NEGOTIATING("협상 중", List.of(MatchingStatus.ACCEPTED, MatchingStatus.NEGOTIATING,
            MatchingStatus.CONTRACT_PENDING)),
    CLOSED("종료됨", List.of(MatchingStatus.REJECTED, MatchingStatus.NEGOTIATION_FAILED,
            MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
            MatchingStatus.COMPLETION_PENDING, MatchingStatus.CLOSED, MatchingStatus.TERMINATED));

    private final String label;
    private final List<MatchingStatus> statuses;
}
