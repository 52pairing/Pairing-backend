package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, Long> {

    Optional<AccountJpaEntity> findByEmailAndRoleAndDeletedAtIsNull(String email, Role role);

    // 중복 판정은 삭제 여부와 무관하게 본다. DB 유니크 제약이 (email, role) 이라 soft delete 된 행도 자리를 차지한다.
    boolean existsByEmailAndRole(String email, Role role);

    boolean existsByPhoneAndRole(String phone, Role role);

    List<AccountJpaEntity> findAllByNameAndPhoneAndDeletedAtIsNull(String name, String phone);

    Optional<AccountJpaEntity> findByEmailAndRoleAndNameAndPhoneAndDeletedAtIsNull(
            String email, Role role, String name, String phone);

    boolean existsByEmailHashAndRoleAndRejoinAvailableAtAfter(String emailHash, Role role, LocalDateTime now);

    boolean existsByPhoneHashAndRoleAndRejoinAvailableAtAfter(String phoneHash, Role role, LocalDateTime now);

    // purgeAt 은 탈퇴할 때만 채워지고 파기하면 다시 null 이 된다. 그래서 이 조건 하나로
    // "탈퇴했고, 기한이 지났고, 아직 파기 안 된" 계정만 정확히 잡힌다.
    List<AccountJpaEntity> findByPurgeAtBeforeOrderByPurgeAtAsc(LocalDateTime now, Pageable pageable);

    // 탈퇴·정지 계정은 등급을 매길 대상이 아니다. status 로만 걸러도 되지만 deleted_at 까지 보는 게
    // 다른 조회들과 같은 기준이다.
    @Query("""
            select a.id from AccountJpaEntity a
            where a.role = :role
              and a.status = com.pairing.account.domain.model.AccountStatus.ACTIVE
              and a.deletedAt is null
              and a.id > :afterId
            order by a.id asc
            """)
    List<Long> findActiveIdsByRole(@Param("role") Role role, @Param("afterId") Long afterId, Pageable pageable);
}
