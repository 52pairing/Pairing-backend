package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 내 프로젝트 목록 탭. 화면의 탭 하나가 여러 상태를 묶는다.
 *
 * <p>매칭중 탭 안에서는 카드마다 "모집 중 / 협상 중 / 계약 대기" 배지로 세부 상태를 보여준다.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectTab {

    REGISTERED("등록 완료", List.of(ProjectStatus.REGISTERED)),
    MATCHING("매칭중", List.of(ProjectStatus.RECRUITING, ProjectStatus.NEGOTIATING,
            ProjectStatus.CONTRACT_PENDING)),
    IN_PROGRESS("진행 중", List.of(ProjectStatus.IN_PROGRESS)),
    COMPLETION_PENDING("완료 대기", List.of(ProjectStatus.COMPLETION_PENDING)),
    CLOSED("종료", List.of(ProjectStatus.CLOSED)),
    CANCELED("취소됨", List.of(ProjectStatus.CANCELED));

    private final String label;
    private final List<ProjectStatus> statuses;
}
