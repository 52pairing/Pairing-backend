package com.pairing.project.domain.model;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.util.List;

/**
 * 포지션 수정 1건. positionId 가 null 이면 새로 추가한다.
 *
 * <p>presentation 의 PositionUpdateRequest 를 도메인이 직접 알지 않도록 중간에 둔다.
 */
public record PositionUpdate(
        Long positionId,
        JobCategory jobCategory,
        JobRole jobRole,
        int minCareerYears,
        int headcount,
        List<SkillCode> skills
) {
}