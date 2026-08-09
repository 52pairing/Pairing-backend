package com.pairing.project.application.command;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.util.List;

/**
 * 등록 전 사전 검수 입력. (정책 P02)
 *
 * <p>projectId 가 없다. 등록 위저드 5단계는 아직 저장 전이라 화면이 들고 있는 값으로만 집계한다.
 * 집계는 직무와 요구 스킬만 쓰므로 경력·예산·기본 정보는 담지 않는다.
 */
public record PreReviewCommand(List<Position> positions) {

    public record Position(JobRole jobRole, int headcount, List<SkillCode> skills) {
    }
}
