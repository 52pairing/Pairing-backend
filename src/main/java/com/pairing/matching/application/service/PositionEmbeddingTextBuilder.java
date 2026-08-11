package com.pairing.matching.application.service;

import com.pairing.matching.application.result.ProjectPositionSummary;

/**
 * 포지션 임베딩용 텍스트 조립. 최초 모집 시작(임베딩 최초 생성)과 모집 시작 후 프로젝트 수정
 * (임베딩 재생성) 양쪽에서 같은 규칙을 써야 해서 공유한다.
 *
 * <p><b>{@code mainTask}(담당업무) 추가 — 2026-08-11.</b> 원래 "노출 여부가 팀 결정 대기(Task #4)"라
 * 빼뒀는데, 그 결정은 2026-08-09에 이미 났다(`.ai/STATE.md` "매칭 요청 상세에 mainTask 노출").
 * 결정이 끝난 걸 모르고 남아 있던 제외였다. 대조 대상(…↔ 프로젝트설명+<b>담당업무</b>+업무범위+
 * 우대사항, 결정 1)에 원래부터 들어 있던 항목이다.
 *
 * <p>{@code currentSituation}(현재 상황)은 계속 안 넣는다 — 3번 답변대로 프로젝트 배경 설명이라
 * 요구조건이 아니고, 프리랜서 쪽에 대응하는 말도 없다.
 *
 * <p>예산·시작희망일을 안 넣는 이유는 {@link FreelancerEmbeddingTextBuilder} 주석 참고(숫자는
 * 임베딩으로 비교가 안 된다).
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
                summary.mainTask() != null ? summary.mainTask() : "",
                summary.detailScope() != null ? summary.detailScope() : "",
                summary.extraNote() != null ? summary.extraNote() : "");
    }
}
