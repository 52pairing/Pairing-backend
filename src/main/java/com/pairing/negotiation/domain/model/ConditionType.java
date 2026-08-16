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

    /**
     * 이 쟁점에서 <b>해당 역할의 마지노선을 어느 방향으로 비교하는가</b>. 방향의 단일 진실 원본이다
     * (가드·프롬프트·응답이 모두 이걸 읽는다).
     *
     * <ul>
     *   <li>AMOUNT·PERIOD: 클라=상한(MAX), 프리=하한(MIN). 프리는 더 받으려 하고 클라는 덜 주려 한다.</li>
     *   <li>START_DATE: <b>양측 모두 상한(MAX)</b> — "늦어도 이 날까지 시작". 프리가 그보다 일찍 시작
     *       못 하는 하한은 마지노선이 아니라 가용 시작일(freelancerValue)이 담당한다.</li>
     *   <li>WORK_STYLE·WORK_FORM: 크기 비교 없음(CHOICE).</li>
     *   <li>SCOPE·OTHER: 기준 없음(NONE).</li>
     * </ul>
     */
    public FloorDirection floorDirectionFor(PartyRole role) {
        return switch (this) {
            case AMOUNT, PERIOD -> role == PartyRole.CLIENT ? FloorDirection.MAX : FloorDirection.MIN;
            case START_DATE -> FloorDirection.MAX;
            case WORK_STYLE, WORK_FORM -> FloorDirection.CHOICE;
            case SCOPE, OTHER -> FloorDirection.NONE;
        };
    }
}
