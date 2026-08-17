package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreelancerConditionRepository {

    FreelancerCondition save(FreelancerCondition condition);

    Optional<FreelancerCondition> findByAccountId(Long accountId);

    /**
     * 여러 계정의 조건을 한 번에. 매칭 도메인이 노출 확정 시점에 프리랜서마다
     * {@link #findByAccountId} 를 부르면 N+1 이라 배치로 받는다.
     *
     * <p>조건을 등록하지 않은 계정은 결과에 없다. 부르는 쪽이 없는 계정을 걸러 쓴다.
     */
    List<FreelancerCondition> findByAccountIdIn(Collection<Long> accountIds);

    /**
     * 직무가 같고 요구 스킬 중 {@code minSkillMatchCount} 개 이상을 보유하며 이력서를 완성한 계정 id.
     *
     * <p>계정 자격(활성·AI 매칭 동의)은 여기서 보지 않는다. account 도메인 소관이라
     * 호출부가 그 결과를 다시 걸러야 한다.
     *
     * <p>ALL/ANY 같은 규칙은 호출부가 개수로 환산해서 넘긴다. 여기는 세기만 한다.
     */
    List<Long> findMatchableAccountIds(JobRole jobRole, Collection<SkillCode> skills, int minSkillMatchCount);
}
