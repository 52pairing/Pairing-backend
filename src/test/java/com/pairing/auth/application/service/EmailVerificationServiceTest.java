package com.pairing.auth.application.service;

import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.SendCodeCommand;
import com.pairing.auth.application.port.EmailSendLimitPort;
import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.application.result.SendCodeResult;
import com.pairing.auth.domain.model.EmailVerification;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.domain.repository.EmailVerificationRepository;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 인증코드 발송 제한과 검증 규칙. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@pairing.com";
    private static final String CODE = "123456";
    private static final String CODE_HASH = "$2a$10$codehash";

    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private VerificationAttemptRecorder verificationAttemptRecorder;
    @Mock
    private EmailSendLimitPort emailSendLimitPort;
    @Mock
    private VerifiedMarkerPort verifiedMarkerPort;
    @Mock
    private MailSenderPort mailSenderPort;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthSettings authSettings;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        authSettings = new AuthSettings();
        emailVerificationService = new EmailVerificationService(emailVerificationRepository,
                verificationAttemptRecorder, emailSendLimitPort, verifiedMarkerPort, mailSenderPort,
                passwordEncoder, authSettings);
    }

    private EmailVerification issued() {
        return EmailVerification.reconstitute(1L, EMAIL, VerificationPurpose.SIGNUP, CODE_HASH,
                LocalDateTime.now().plusMinutes(3), null, 0);
    }

    @Test
    @DisplayName("코드를 발송하면 해시로 저장하고 남은 발송 횟수를 알려준다")
    void sendStoresHashedCode() {
        given(emailSendLimitPort.increaseAndGet(anyString(), any(Duration.class))).willReturn(1);
        given(passwordEncoder.encode(anyString())).willReturn(CODE_HASH);
        given(emailVerificationRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        SendCodeResult result = emailVerificationService.send(
                new SendCodeCommand("  User@Pairing.com ", VerificationPurpose.SIGNUP));

        assertThat(result.remainingSendCount()).isEqualTo(authSettings.getEmailSendLimit() - 1);
        assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
        // 정규화된 소문자 이메일로 발송돼야 대소문자만 다른 재요청이 같은 카운터를 쓴다.
        verify(mailSenderPort).send(eq(EMAIL), anyString(), anyString());
    }

    @Test
    @DisplayName("1시간 15회를 넘기면 AU_003으로 막고 메일을 보내지 않는다")
    void sendLimitExceeded() {
        given(emailSendLimitPort.increaseAndGet(anyString(), any(Duration.class)))
                .willReturn(authSettings.getEmailSendLimit() + 1);

        assertThatThrownBy(() -> emailVerificationService.send(
                new SendCodeCommand(EMAIL, VerificationPurpose.SIGNUP)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);

        verify(mailSenderPort, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("코드가 맞으면 인증 완료 마커를 남긴다")
    void confirmSuccess() {
        given(emailVerificationRepository.findLatest(EMAIL, VerificationPurpose.SIGNUP))
                .willReturn(Optional.of(issued()));
        given(passwordEncoder.matches(CODE, CODE_HASH)).willReturn(true);

        emailVerificationService.confirm(new ConfirmCodeCommand(EMAIL, VerificationPurpose.SIGNUP, CODE));

        verify(verifiedMarkerPort).mark(EMAIL, VerificationPurpose.SIGNUP, authSettings.getVerifiedMarkerTtl());
    }

    @Test
    @DisplayName("코드가 틀려도 시도 횟수는 별도 트랜잭션에 기록된다")
    void confirmMismatchStillRecordsAttempt() {
        given(emailVerificationRepository.findLatest(EMAIL, VerificationPurpose.SIGNUP))
                .willReturn(Optional.of(issued()));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new ConfirmCodeCommand(EMAIL, VerificationPurpose.SIGNUP, "000000")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_MISMATCH);

        // 롤백에 휩쓸리면 무제한 대입이 가능해진다. 기록은 반드시 남아야 한다.
        verify(verificationAttemptRecorder).record(any(EmailVerification.class));
        verify(verifiedMarkerPort, never()).mark(anyString(), any(), any());
    }

    @Test
    @DisplayName("만료된 코드는 AU_005로 막는다")
    void confirmExpired() {
        EmailVerification expired = EmailVerification.reconstitute(1L, EMAIL, VerificationPurpose.SIGNUP,
                CODE_HASH, LocalDateTime.now().minusSeconds(1), null, 0);
        given(emailVerificationRepository.findLatest(EMAIL, VerificationPurpose.SIGNUP))
                .willReturn(Optional.of(expired));

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new ConfirmCodeCommand(EMAIL, VerificationPurpose.SIGNUP, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }

    @Test
    @DisplayName("시도 횟수를 넘긴 코드는 AU_012로 폐기 처리한다")
    void confirmAttemptExceeded() {
        EmailVerification exhausted = EmailVerification.reconstitute(1L, EMAIL, VerificationPurpose.SIGNUP,
                CODE_HASH, LocalDateTime.now().plusMinutes(3), null,
                authSettings.getEmailCodeMaxAttempt());
        given(emailVerificationRepository.findLatest(EMAIL, VerificationPurpose.SIGNUP))
                .willReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new ConfirmCodeCommand(EMAIL, VerificationPurpose.SIGNUP, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);

        verify(verificationAttemptRecorder, never()).record(any());
    }

    @Test
    @DisplayName("발급 이력이 없으면 만료로 간주한다")
    void confirmWithoutIssuedCode() {
        given(emailVerificationRepository.findLatest(anyString(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new ConfirmCodeCommand(EMAIL, VerificationPurpose.SIGNUP, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }
}
