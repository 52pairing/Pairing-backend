package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

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

    /** 일이 실제로 시작된 뒤의 상태들. */
    private static final Set<ProjectStatus> STARTED =
            EnumSet.of(IN_PROGRESS, COMPLETION_PENDING, CLOSED);

    private final String label;

    /**
     * 프로젝트가 실제로 시작됐는가.
     *
     * <p>중간에 닫을 때 도착 상태를 가른다. 시작 전이면 취소됨, 시작 후여야 종료다.
     * 협상중에 일부 인원이 계약을 맺었더라도 프로젝트 자체는 아직 시작 전이다.
     */
    public boolean isStarted() {
        return STARTED.contains(this);
    }
}
