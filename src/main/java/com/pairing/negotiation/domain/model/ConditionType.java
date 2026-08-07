package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 협상 대상 조건. 서로 맞지 않는 항목만 협상 대상이 된다. (요구사항 R08)
 */
@Getter
@RequiredArgsConstructor
public enum ConditionType {

    AMOUNT("금액"),
    PERIOD("기간"),
    START_DATE("시작일"),
    WORK_STYLE("근무 방식"),
    WORK_FORM("근무 형태"),
    SCOPE("업무 범위"),
    OTHER("기타");

    private final String label;
}
