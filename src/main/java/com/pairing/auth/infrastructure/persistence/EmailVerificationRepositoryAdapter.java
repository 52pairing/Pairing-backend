package com.pairing.auth.infrastructure.persistence;

import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.domain.repository.EmailVerificationRepository;
import com.pairing.auth.infrastructure.mapper.EmailVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {

    private final SpringDataEmailVerificationRepository springDataRepository;
    private final EmailVerificationMapper emailVerificationMapper;

    @Override
    public EmailVerification save(EmailVerification emailVerification) {
        EmailVerificationJpaEntity saved =
                springDataRepository.save(emailVerificationMapper.toJpaEntity(emailVerification));
        return emailVerificationMapper.toDomain(saved);
    }

    @Override
    public Optional<EmailVerification> findLatest(String email, VerificationPurpose purpose) {
        return springDataRepository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
                .map(emailVerificationMapper::toDomain);
    }
}
