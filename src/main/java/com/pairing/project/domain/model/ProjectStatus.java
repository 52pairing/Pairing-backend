package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    private final String label;
}
