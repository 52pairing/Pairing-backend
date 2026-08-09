package com.pairing.freelancer.application.usecase;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.util.List;

/**
 * 다른 도메인이 조건에 맞는 프리랜서 수를 세는 인바운드 포트.
 *
 * <p>"무엇으로 셀지" 는 호출하는 쪽이 {@link CandidateQuery} 로 정하고,
 * "누가 후보 자격인지" 는 이 도메인이 정한다. 자격 기준이 바뀌어도 호출부는 고치지 않는다.
 *
 * <p>현재 사용처는 프로젝트 사전 검수(정책 P02)다. AI 를 쓰지 않는 단순 필터다.
 */
public interface FreelancerCandidateCountUseCase {

    /** 조건을 만족하면서 매칭 가능한 프리랜서 수. */
    long count(CandidateQuery query);

    /**
     * 집계 조건.
     *
     * @param jobRole    완전 일치
     * @param skills     요구 스킬. 비어 있으면 0 을 준다
     * @param skillMatch 요구 스킬을 모두 봐야 하는지, 하나만 겹쳐도 되는지
     */
    record CandidateQuery(JobRole jobRole, List<SkillCode> skills, SkillMatch skillMatch) {

        public static CandidateQuery all(JobRole jobRole, List<SkillCode> skills) {
            return new CandidateQuery(jobRole, skills, SkillMatch.ALL);
        }
    }

    enum SkillMatch { ALL, ANY }
}
