package com.pairing.auth.infrastructure.persistence;

import com.pairing.auth.domain.model.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataEmailVerificationRepository extends JpaRepository<EmailVerificationJpaEntity, Long> {

    // IDENTITY PK 는 발급 순서대로 증가하므로 id 내림차순이 곧 최신순이다.
    Optional<EmailVerificationJpaEntity> findTopByEmailAndPurposeOrderByIdDesc(String email,
                                                                              VerificationPurpose purpose);
}
