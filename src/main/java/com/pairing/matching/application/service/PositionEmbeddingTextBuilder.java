package com.pairing.matching.application.service;

import com.pairing.matching.application.result.ProjectPositionSummary;

/**
 * 포지션 임베딩용 텍스트 조립. 최초 모집 시작(임베딩 최초 생성)과 모집 시작 후 프로젝트 수정
 * (임베딩 재생성) 양쪽에서 같은 규칙을 써야 해서 공유한다.
 *
 * <p>임베딩 대조 대상(자기소개+경력사항 ↔ 프로젝트설명+담당업무+업무범위+우대사항, .ai/STATE.md
 * "확정된 설계 결정 1")과 완전히 같지는 않다 — mainTask/currentSituation은 노출 여부 자체가
 * 팀 결정 대기 항목이라(Task #4) 아직 안 넣는다. detailScope/extraNote는 결정 대기가 아니라
 * 단순히 안 이어져 있던 것이라 이번에 채웠다(HANDOFF 22번).
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
                summary.periodLabel(),
                summary.detailScope() != null ? summary.detailScope() : "",
                summary.extraNote() != null ? summary.extraNote() : "");
    }
}
