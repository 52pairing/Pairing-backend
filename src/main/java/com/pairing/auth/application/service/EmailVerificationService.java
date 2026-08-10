package com.pairing.auth.application.service;

import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.SendCodeCommand;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.auth.application.policy.VerificationCodeGenerator;
import com.pairing.auth.application.port.EmailSendLimitPort;
import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.application.result.SendCodeResult;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.domain.repository.EmailVerificationRepository;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증코드 발송/확인.
 *
 * <p>코드는 해시로만 저장하고, 인증 완료 사실은 짧은 마커로 따로 남긴다.
 * 발송 횟수는 1시간 15회로 제한한다. (요구사항 R13)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService implements EmailVerificationUseCase {

    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationAttemptRecorder verificationAttemptRecorder;
    private final EmailSendLimitPort emailSendLimitPort;
    private final VerifiedMarkerPort verifiedMarkerPort;
    private final MailSenderPort mailSenderPort;
    private final PasswordEncoder passwordEncoder;
    private final AuthSettings authSettings;

    @Override
    public SendCodeResult send(SendCodeCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());

        int sendCount = emailSendLimitPort.increaseAndGet(email, authSettings.getEmailSendWindow());
        if (sendCount > authSettings.getEmailSendLimit()) {
            throw new BusinessException(AuthErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }

        String code = VerificationCodeGenerator.generate();

        // 코드는 평문으로 남기지 않는다. 비밀번호와 같은 인코더를 쓰면 별도 해시 유틸이 필요 없다.
        EmailVerification verification = emailVerificationRepository.save(EmailVerification.issue(
                email,
                command.purpose(),
                passwordEncoder.encode(code),
                authSettings.getEmailCodeTtl()
        ));

        mailSenderPort.send(email, subjectOf(command), bodyOf(code));

        return new SendCodeResult(
                verification.getExpiresAt(),
                Math.max(0, authSettings.getEmailSendLimit() - sendCount)
        );
    }

    @Override
    public void confirm(ConfirmCodeCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());

        EmailVerification verification = emailVerificationRepository
                .findLatest(email, command.purpose())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED));

        if (verification.isExpired()) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (verification.isAttemptExceeded(authSettings.getEmailCodeMaxAttempt())) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
        }

        // 실패해도 시도 횟수는 남아야 무차별 대입을 막을 수 있다.
        // 아래에서 예외를 던지면 현재 트랜잭션이 롤백되므로 별도 트랜잭션에 기록한다.
        verificationAttemptRecorder.record(verification);

        if (!passwordEncoder.matches(command.code(), verification.getCodeHash())) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        verification.markVerified();
        emailVerificationRepository.save(verification);

        verifiedMarkerPort.mark(email, command.purpose(), authSettings.getVerifiedMarkerTtl());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVerified(String email, VerificationPurpose purpose) {
        return verifiedMarkerPort.isVerified(ContactPolicy.normalizeEmail(email), purpose);
    }

    @Override
    public void clearVerification(String email, VerificationPurpose purpose) {
        verifiedMarkerPort.clear(ContactPolicy.normalizeEmail(email), purpose);
    }

    private String subjectOf(SendCodeCommand command) {
        return "[페어링] %s 인증코드".formatted(command.purpose().getLabel());
    }

    private String bodyOf(String code) {
        return """
                인증코드: %s

                유효시간은 %d분입니다. 시간이 지나면 새 코드를 다시 요청해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다."""
                .formatted(code, authSettings.getEmailCodeTtl().toMinutes());
    }
}
