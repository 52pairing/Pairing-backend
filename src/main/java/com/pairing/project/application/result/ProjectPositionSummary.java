package com.pairing.project.application.result;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 + 포지션 1건 요약.
 *
 * <p>다른 도메인이 프로젝트 정보를 한 번에 읽을 때 쓴다. 화면 라벨이나 조합 문자열은
 * 만들지 않는다. 소비하는 쪽이 자기 화면에 맞게 조립한다.
 *
 * <p>회사명·업종·직원수는 담지 않는다. account 도메인의
 * {@code AccountQueryUseCase.findClientProfileById} 로 직접 받는다.
 */
public record ProjectPositionSummary(

        Long projectId,
        String title,

        // 포지션 단위
        JobRole jobRole,
        List<SkillCode> skills,
        int minCareerYears,
        int headcount,

        // 프로젝트 단위. 포지션별로 다르지 않다.
        WorkStyle workStyle,
        WorkForm workForm,
        int periodValue,
        PeriodUnit periodUnit,
        LocalDate startDesiredDate,

        /** 프로젝트 전체 예산. 원 단위, 부가세 별도. */
        Long budgetAmount
) {
}