package com.pairing.matching.infrastructure.directory;

import com.pairing.freelancer.application.result.FreelancerCandidateSummaryResult;
import com.pairing.freelancer.application.usecase.FreelancerCandidateSummaryUseCase;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link FreelancerDirectoryPort}의 부분 실구현.
 *
 * <p>{@code findCardSummary}는 freelancer 도메인의 {@code FreelancerCandidateSummaryUseCase}로 실제 조회한다.
 *
 * <p><b>{@code resolveFreelancerId}/{@code findCondition}은 아직 스텁이다.</b> account_id ↔
 * freelancer_profile.id 양방향 조회가 account 도메인에 아직 없다(2번이 1번에게 승인 요청한 상태,
 * 2026-08-08 기준 대기 중). 그 메서드가 생기면 이 두 메서드만 실구현으로 교체한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreelancerDirectoryAdapter implements FreelancerDirectoryPort {

    private final FreelancerCandidateSummaryUseCase freelancerCandidateSummaryUseCase;

    @Override
    public Long resolveFreelancerId(Long accountId) {
        log.warn("[프리랜서 일부 미연동] accountId->freelancerId 변환이 아직 없어 accountId를 그대로 씁니다. accountId={}",
                accountId);
        return accountId;
    }

    @Override
    public FreelancerCardSummary findCardSummary(Long freelancerId) {
        FreelancerCandidateSummaryResult summary = freelancerCandidateSummaryUseCase.getSummary(freelancerId);
        return new FreelancerCardSummary(summary.name(), summary.profileImageUrl(), summary.grade(),
                summary.ratingAverage(), summary.reviewCount());
    }

    @Override
    public FreelancerConditionResponse findCondition(Long freelancerId) {
        log.warn("[프리랜서 일부 미연동] freelancerId 기준 조건 조회가 아직 없어 placeholder를 돌려줍니다. freelancerId={}",
                freelancerId);
        return new FreelancerConditionResponse(
                freelancerId, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new FreelancerConditionResponse.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new FreelancerConditionResponse.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED)));
    }
}
