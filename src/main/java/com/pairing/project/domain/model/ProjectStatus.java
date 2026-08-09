package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 프로젝트 전체 상태. (요구사항 R29)
 *
 * <p>여러 명을 모집하면 인원별 상태가 다를 수 있어, 프로젝트는 가장 앞선 단계를 대표로 표시하고
 * 세부 현황은 statusNote 문구로 안내한다. 예: "2 / 3명 협상중 · 1명 모집중"
 */
@Getter
@RequiredArgsConstructor
public enum ProjectStatus {

    REGISTERED("등록 완료"),
    RECRUITING("모집중"),
    NEGOTIATING("협상중"),
    CONTRACT_PENDING("계약 대기"),
    IN_PROGRESS("진행중"),
    COMPLETION_PENDING("완료 대기"),
    CLOSED("종료"),
    CANCELED("취소됨");

    /**
     * 정상 진행 순서. 취소됨은 흐름 밖이라 넣지 않는다.
     *
     * <p>선언 순서(ordinal)에 기대지 않는다. enum 상수를 재배치해도 흐름이 깨지지 않아야 한다.
     */
    private static final List<ProjectStatus> FLOW = List.of(
            REGISTERED, RECRUITING, NEGOTIATING, CONTRACT_PENDING,
            IN_PROGRESS, COMPLETION_PENDING, CLOSED);

    private final String label;

    /**
     * 이 상태가 {@code other} 보다 앞 단계인가.
     *
     * <p>여러 명을 모집하면 인원마다 진행 속도가 달라 같은 전이가 여러 번 들어온다.
     * 프로젝트는 가장 앞선 단계를 대표로 표시하므로, 뒤처진 인원 때문에 되돌아가면 안 된다.
     *
     * <p>흐름 밖(취소됨)이면 항상 false 다. 취소된 프로젝트는 전이 자체를 막는 쪽에서 걸러진다.
     */
    public boolean isBefore(ProjectStatus other) {
        int mine = FLOW.indexOf(this);
        int theirs = FLOW.indexOf(other);
        return mine >= 0 && theirs >= 0 && mine < theirs;
    }
}
