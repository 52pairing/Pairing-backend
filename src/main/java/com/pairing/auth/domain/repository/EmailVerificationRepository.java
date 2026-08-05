package com.pairing.auth.domain.repository;

import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.domain.model.VerificationPurpose;

import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification emailVerification);

    /** 같은 이메일/용도로 가장 마지막에 발급된 코드. 재발송하면 이전 코드는 무시된다. */
    Optional<EmailVerification> findLatest(String email, VerificationPurpose purpose);
}
