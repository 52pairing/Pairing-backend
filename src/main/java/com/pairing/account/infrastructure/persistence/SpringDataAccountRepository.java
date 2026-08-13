package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
