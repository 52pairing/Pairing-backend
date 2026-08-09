package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpringDataFreelancerConditionRepository extends JpaRepository<FreelancerConditionJpaEntity, Long> {

    Optional<FreelancerConditionJpaEntity> findByAccountId(Long accountId);

    /**
     * 사전 검수 1차 후보. 직무·스킬·이력서까지만 본다. (정책 P02)
     *
     * <p>보유 스킬 중 요구 스킬과 겹치는 개수가 {@code minSkillMatchCount} 이상이어야 통과한다.
     * ALL 이면 요구 스킬 개수를, ANY 면 1 을 넘긴다. DISTINCT 는 condition_skill 에 같은 스킬이
     * 두 번 들어가 있어도 개수가 부풀지 않게 하려는 것이다.
     *
     * <p>계정 자격(활성·AI 매칭 동의)은 account 도메인 소유라 여기서 읽지 않는다.
     * 그쪽 엔티티를 조인하면 account 가 구조를 바꿀 때 이 쿼리가 같이 깨진다.
     */
    @Query("""
            SELECT c.accountId FROM FreelancerConditionJpaEntity c
             WHERE c.jobRole = :jobRole
               AND (SELECT COUNT(DISTINCT s.skillCode)
                      FROM FreelancerConditionJpaEntity c2 JOIN c2.skills s
                     WHERE c2.id = c.id AND s.skillCode IN :skills) >= :minSkillMatchCount
               AND EXISTS (SELECT 1 FROM ResumeJpaEntity r
                            WHERE r.accountId = c.accountId
                              AND r.status = com.pairing.freelancer.domain.model.ResumeStatus.COMPLETED)
            """)
    List<Long> findMatchableAccountIds(@Param("jobRole") JobRole jobRole,
                                       @Param("skills") Collection<SkillCode> skills,
                                       @Param("minSkillMatchCount") int minSkillMatchCount);
}
