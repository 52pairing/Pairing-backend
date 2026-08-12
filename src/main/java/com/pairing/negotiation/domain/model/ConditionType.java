package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 협상 대상 조건. 서로 맞지 않는 항목만 협상 대상이 된다. (요구사항 R08)
 *
 * <p>{@link #floorComparison} 은 이 쟁점의 마지노선을 어떻게 비교하는지다.
 * {@link com.pairing.negotiation.domain.service.NegotiationFloorGuard} 가 실제로 쓰는 규칙과
 * <b>같은 값이어야 한다</b> — 새 쟁점을 추가하면 가드의 분기와 여기를 함께 손볼 것.
 */
@Getter
@RequiredArgsConstructor
public enum ConditionType {

    AMOUNT("금액", FloorComparison.RANGE),
    PERIOD("기간", FloorComparison.RANGE),
    START_DATE("시작일", FloorComparison.RANGE),
    WORK_STYLE("근무 방식", FloorComparison.CHOICE),
    WORK_FORM("근무 형태", FloorComparison.CHOICE),
    // 자유 텍스트라 대소 관계도 허용값 목록도 없다. 가드가 통과시킨다.
    SCOPE("업무 범위", FloorComparison.NONE),
    OTHER("기타", FloorComparison.NONE);

    private final String label;
    private final FloorComparison floorComparison;
}
