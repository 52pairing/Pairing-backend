package com.pairing.matching.infrastructure.directory;

import com.pairing.freelancer.domain.model.FreelancerGrade;
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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link FreelancerDirectoryPort}의 임시 구현.
 *
 * <p>freelancer 도메인에 아직 실제 영속 계층이 없어(2026-08-07 기준 domain/model 에 enum만 존재)
 * 고정값을 돌려준다. freelancer 도메인이 실제 조회 코드를 갖추면 이 클래스를 실제 리포지토리 호출로
 * 교체한다 — 호출부(matching 서비스 계층)는 수정할 필요 없다.
 */
@Component
public class StubFreelancerDirectoryAdapter implements FreelancerDirectoryPort {

    @Override
    public Long resolveFreelancerId(Long accountId) {
        return accountId;
    }

    @Override
    public FreelancerCardSummary findCardSummary(Long freelancerId) {
        return new FreelancerCardSummary("홍길동", "profiles/uuid.png", FreelancerGrade.SENIOR, 4.5, 12);
    }

    @Override
    public FreelancerConditionResponse findCondition(Long freelancerId) {
        return new FreelancerConditionResponse(
                freelancerId, JobCategory.DEVELOPMENT, JobRole.BACKEND, null,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, PayUnit.MONTHLY, 6_500_000L, 5_500_000L,
                LocalDate.now().plusDays(14), false, 6, PeriodUnit.MONTH, true, 5,
                List.of(new FreelancerConditionResponse.Skill(SkillCode.JAVA, SkillLevel.ADVANCED),
                        new FreelancerConditionResponse.Skill(SkillCode.SPRING_BOOT, SkillLevel.ADVANCED)));
    }
}
