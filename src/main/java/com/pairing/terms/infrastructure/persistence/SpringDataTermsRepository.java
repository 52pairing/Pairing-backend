package com.pairing.terms.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SpringDataTermsRepository extends JpaRepository<TermsJpaEntity, Long> {

    // 공통 약관(target_role IS NULL)과 역할 전용 약관을 함께 가져온다.
    // 같은 code의 여러 버전이 나올 수 있어 시행일 내림차순으로 정렬하고, 최신 선택은 서비스가 한다.
    @Query("""
            select t from TermsJpaEntity t
            where (t.targetRole is null or t.targetRole = :targetRole)
              and t.effectiveAt <= :now
            order by t.code asc, t.effectiveAt desc
            """)
    List<TermsJpaEntity> findEffectiveByRole(@Param("targetRole") String targetRole,
                                             @Param("now") LocalDateTime now);

    List<TermsJpaEntity> findAllByIdIn(Collection<Long> ids);
}
