package com.pairing.project.application.port;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.util.List;

/**
 * 사전 검수가 쓰는 프리랜서 후보 집계. (정책 P02)
 *
 * <p>무엇으로 셀지는 이쪽이 정하고, 누가 후보 자격인지는 freelancer 도메인이 정한다.
 * 자격 기준이 바뀌어도 검수 로직은 고치지 않는다.
 */
public interface FreelancerCandidateCounterPort {

    /** 조건을 만족하면서 매칭 가능한 프리랜서 수. */
    long count(CandidateCriteria criteria);

    /**
     * 검수 조건.
     *
     * @param requireAllSkills true 면 요구 스킬 전부 보유, false 면 하나만 겹쳐도 후보
     */
    record CandidateCriteria(JobRole jobRole, List<SkillCode> requiredSkills, boolean requireAllSkills) {
    }
}
