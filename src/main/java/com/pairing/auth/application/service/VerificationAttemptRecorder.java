package com.pairing.auth.application.service;

import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.domain.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증코드 입력 시도 횟수를 별도 트랜잭션에 기록한다.
 *
 * <p>코드가 틀리면 곧바로 예외를 던지는데, 같은 트랜잭션에서 증가시키면 롤백과 함께 사라진다.
 * 그러면 "5회 초과 시 코드 폐기"가 동작하지 않아 6자리 코드를 무제한으로 시도할 수 있게 된다.
 *
 * <p>자기 호출(self-invocation)은 프록시를 타지 않아 REQUIRES_NEW가 걸리지 않으므로 별도 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class VerificationAttemptRecorder {

    private final EmailVerificationRepository emailVerificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(EmailVerification verification) {
        verification.increaseAttempt();
        emailVerificationRepository.save(verification);
    }
}
