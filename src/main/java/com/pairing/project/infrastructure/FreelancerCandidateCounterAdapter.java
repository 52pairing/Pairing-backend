package com.pairing.project.infrastructure;

import com.pairing.freelancer.application.usecase.FreelancerCandidateCountUseCase;
import com.pairing.freelancer.application.usecase.FreelancerCandidateCountUseCase.CandidateQuery;
import com.pairing.freelancer.application.usecase.FreelancerCandidateCountUseCase.SkillMatch;
import com.pairing.project.application.port.FreelancerCandidateCounterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** FreelancerCandidateCounterPort 구현. 검수 조건을 freelancer 도메인 질의로 옮긴다. */
@Component
@RequiredArgsConstructor
public class FreelancerCandidateCounterAdapter implements FreelancerCandidateCounterPort {

    private final FreelancerCandidateCountUseCase freelancerCandidateCountUseCase;

    @Override
    public long count(CandidateCriteria criteria) {
        return freelancerCandidateCountUseCase.count(new CandidateQuery(
                criteria.jobRole(),
                criteria.requiredSkills(),
                criteria.requireAllSkills() ? SkillMatch.ALL : SkillMatch.ANY));
    }
}
