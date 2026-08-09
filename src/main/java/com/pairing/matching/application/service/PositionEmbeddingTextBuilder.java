package com.pairing.matching.application.service;

import com.pairing.matching.application.result.ProjectPositionSummary;

/**
 * 포지션 임베딩용 텍스트 조립. 최초 모집 시작(임베딩 최초 생성)과 모집 시작 후 프로젝트 수정
 * (임베딩 재생성) 양쪽에서 같은 규칙을 써야 해서 공유한다.
 *
 * <p>임베딩 대조 대상(자기소개+경력사항 ↔ 프로젝트설명+담당업무+업무범위+우대사항, .ai/STATE.md
 * "확정된 설계 결정 1")과 완전히 같지는 않다 — mainTask/currentSituation/업무범위/우대사항은
 * 아직 매칭 쪽 요약({@link ProjectPositionSummary})에 없다(Task #4 결정 대기, HANDOFF 22번).
 * 결정되면 이 메서드만 채워 넣으면 된다.
 */
final class PositionEmbeddingTextBuilder {

    private PositionEmbeddingTextBuilder() {
    }

    static String buildText(ProjectPositionSummary summary) {
        return String.join("\n",
                summary.projectTitle(),
                summary.jobRole() != null ? summary.jobRole().getLabel() : "",
                String.valueOf(summary.requiredSkills()),
                "경력 " + summary.minCareerYears() + "년 이상",
                summary.workLabel(),
                summary.periodLabel());
    }
}
