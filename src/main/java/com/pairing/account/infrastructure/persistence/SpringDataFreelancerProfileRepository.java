package com.pairing.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpringDataFreelancerProfileRepository extends JpaRepository<FreelancerProfileJpaEntity, Long> {

    Optional<FreelancerProfileJpaEntity> findByAccountIdAndDeletedAtIsNull(Long accountId);

    List<FreelancerProfileJpaEntity> findByAccountIdInAndDeletedAtIsNull(Collection<Long> accountIds);

    Optional<FreelancerProfileJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<FreelancerProfileJpaEntity> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    /**
     * 활성 계정이면서 AI 매칭에 동의했고 매칭을 일시중지하지 않은 프리랜서의 계정 id. (프로젝트 사전 검수)
     *
     * <p>엔티티를 로드하지 않고 id 만 뽑는다. 호출부는 개수 또는 교집합만 쓴다.
     *
     * <p>탈퇴 여부는 {@code account.status} 로 판정한다. PENDING·LOCKED·WITHDRAWN 이 한 번에 걸러진다.
     */
    @Query("""
            SELECT p.accountId FROM FreelancerProfileJpaEntity p
             WHERE p.accountId IN :accountIds
               AND p.aiMatchingAgreed = true
               AND p.matchingPaused = false
               AND EXISTS (SELECT 1 FROM AccountJpaEntity a
                            WHERE a.id = p.accountId
                              AND a.status = com.pairing.account.domain.model.AccountStatus.ACTIVE)
            """)
    List<Long> filterActiveAiMatchingAgreed(@Param("accountIds") Collection<Long> accountIds);
}
