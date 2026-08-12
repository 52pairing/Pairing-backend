package com.pairing.matching.application.result;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 매칭 요청 카드 노출용 프로젝트·포지션 요약. project 도메인 소유 데이터.
 *
 * <p>{@code periodValue}/{@code periodUnit}은 {@code periodLabel}과 별개로 둔다. budgetCap을
 * "월단가"로 계산하려면(BudgetCapCalculator) 기간을 개월 수로 환산해야 하는데, 화면 표기용
 * 문자열(periodLabel)로는 계산할 수 없어서다.
 *
 * <p>{@code totalHeadcount}는 프로젝트 전체 포지션 인원의 합이다. budgetCap은 이 포지션의
 * headcount가 아니라 이 값으로 나눠야 한다(순예산은 프로젝트 전체 기준이라서).
 *
 * <p>{@code currentSituation}(프로젝트 진행 상황)은 <b>임베딩 전용</b>이다. 요청 카드에는 안 나간다
 * (2026-08-09 3번 확인: 카드엔 {@code mainTask}만 노출). 임베딩에는 넣는 것으로 2026-08-11 확정 —
 * "기존 서비스 리뉴얼"과 "신규 구축"은 필요한 사람이 다른데 그 차이가 이 필드에만 있다.
 */
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
        int periodValue,
        PeriodUnit periodUnit,
        LocalDate startDesiredDate,
        Long budgetAmount,
        int headcount,
        int totalHeadcount,
        String currentSituation,
        String mainTask,
        String detailScope,
        String extraNote
) {
}
