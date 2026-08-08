package com.pairing.matching.application.result;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.time.LocalDate;
import java.util.List;

/** 매칭 요청 카드 노출용 프로젝트·포지션 요약. project 도메인 소유 데이터. */
public record ProjectPositionSummary(
        Long projectId,
        String projectTitle,
        String companyName,
        String companyProfile,
        JobRole jobRole,
        List<SkillCode> requiredSkills,
        Integer minCareerYears,
        String workLabel,
        String periodLabel,
        LocalDate startDesiredDate,
        Long budgetAmount,
        int headcount
) {
}
