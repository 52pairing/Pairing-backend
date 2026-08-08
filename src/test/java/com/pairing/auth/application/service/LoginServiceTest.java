package com.pairing.auth.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import com.pairing.auth.application.command.LoginCommand;
import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.auth.application.port.LoginAttemptPort;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 로그인 규칙 검증.
 *
 * <p>실패 사유를 구분해 응답하지 않는 것, IP 차단과 계정 잠금이 각각 동작하는 것이 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginServiceTest {

    private static final String IP = "127.0.0.1";
    private static final String EMAIL = "user@pairing.com";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final String HASH = "$2a$10$hash";

    @Mock
    private AccountQueryUseCase accountQueryUseCase;
    @Mock
    private AccountCommandUseCase accountCommandUseCase;
    @Mock
    private LoginAttemptPort loginAttemptPort;
    @Mock
    private AccountSuspensionPort accountSuspensionPort;
    @Mock
    private AuthTokenIssuer authTokenIssuer;
    @Mock
    private PasswordEncoder passwordEncoder;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        AuthSettings authSettings = new AuthSettings();
        loginService = new LoginService(accountQueryUseCase, accountCommandUseCase, loginAttemptPort,
                accountSuspensionPort, authTokenIssuer, passwordEncoder, authSettings);
    }

    private Account account(Long id, AccountStatus status) {
        return Account.reconstitute(id, EMAIL, HASH, Role.FREELANCER, "홍길동", "01012345678",
                SignupType.EMAIL, status, true, 0, null, false, null, null,
                null, null, null, null, null, null, null, null);
    }

    private LoginCommand command() {
        return new LoginCommand(EMAIL, RAW_PASSWORD, Role.FREELANCER, IP);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 토큰을 발급하고 IP 실패 기록을 지운다")
    void loginSuccess() {
        Account account = account(1L, AccountStatus.ACTIVE);
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(RAW_PASSWORD, HASH)).willReturn(true);
        given(accountCommandUseCase.applyLoginSuccess(1L)).willReturn(account);
        given(authTokenIssuer.issue(account))
                .willReturn(new LoginResult(1L, "FREELANCER", "홍길동", false, "access", "refresh"));

        LoginResult result = loginService.login(command());

        assertThat(result.accountId()).isEqualTo(1L);
        assertThat(result.accessToken()).isEqualTo("access");
        verify(loginAttemptPort).clearFailure(IP);
    }

    @Test
    @DisplayName("계정이 없어도 비밀번호가 틀린 것과 같은 AU_001로 응답한다")
    void unknownAccountLooksTheSameAsWrongPassword() {
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);

        verify(loginAttemptPort).recordFailure(eq(IP), anyInt(), any(), any());
        verify(accountCommandUseCase, never()).applyLoginFailure(any(), anyInt());
    }

    @Test
    @DisplayName("역할 탭이 다르면 계정을 찾지 못해 AU_001이 된다")
    void roleMismatchFails() {
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.CLIENT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(new LoginCommand(EMAIL, RAW_PASSWORD, Role.CLIENT, IP)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 계정 실패 횟수를 올리고 AU_001로 응답한다")
    void wrongPasswordIncreasesFailureCount() {
        Account account = account(1L, AccountStatus.ACTIVE);
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(anyString(), eq(HASH))).willReturn(false);
        given(accountCommandUseCase.applyLoginFailure(eq(1L), anyInt()))
                .willReturn(account(1L, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);

        verify(accountCommandUseCase).applyLoginFailure(1L, 5);
    }

    @Test
    @DisplayName("실패로 잠금 임계치에 도달하면 AU_002로 바꿔 응답한다")
    void reachingThresholdReturnsLocked() {
        Account account = account(1L, AccountStatus.ACTIVE);
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.of(account));
        given(passwordEncoder.matches(anyString(), eq(HASH))).willReturn(false);
        given(accountCommandUseCase.applyLoginFailure(eq(1L), anyInt()))
                .willReturn(account(1L, AccountStatus.LOCKED));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("이미 잠긴 계정은 비밀번호를 확인하지 않고 AU_002로 막는다")
    void lockedAccountIsRejectedBeforePasswordCheck() {
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER))
                .willReturn(Optional.of(account(1L, AccountStatus.LOCKED)));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("차단된 IP는 계정 조회 전에 AU_014로 막는다")
    void blockedIpIsRejectedFirst() {
        given(loginAttemptPort.isBlocked(IP)).willReturn(true);
        given(loginAttemptPort.blockRemaining(IP)).willReturn(Duration.ofMinutes(90));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_BLOCKED);

        verify(accountQueryUseCase, never()).findByEmailAndRole(anyString(), any());
    }

    @Test
    @DisplayName("IP 차단 메시지에 다시 시도할 수 있는 시각이 들어간다")
    void blockedMessageContainsRetryTime() {
        given(loginAttemptPort.isBlocked(IP)).willReturn(true);
        given(loginAttemptPort.blockRemaining(IP)).willReturn(Duration.ofMinutes(90));

        String expectedAt = LocalDateTime.now().plusMinutes(90)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(expectedAt)
                .hasMessageContaining("1시간 30분 남음");
    }

    @Test
    @DisplayName("남은 시간을 못 읽으면 차단 기간(2시간)으로 안내한다")
    void blockedMessageFallsBackToBlockDuration() {
        given(loginAttemptPort.isBlocked(IP)).willReturn(true);
        given(loginAttemptPort.blockRemaining(IP)).willReturn(Duration.ZERO);

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2시간 후에 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("정지된 계정은 AU_022로 막는다")
    void suspendedAccountIsRejected() {
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER))
                .willReturn(Optional.of(account(1L, AccountStatus.ACTIVE)));
        given(accountSuspensionPort.isSuspended(1L)).willReturn(true);

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("소셜 전용 계정은 비밀번호 로그인을 할 수 없다")
    void socialOnlyAccountCannotLoginWithPassword() {
        Account social = Account.reconstitute(1L, EMAIL, null, Role.FREELANCER, "홍길동", "01012345678",
                SignupType.SOCIAL, AccountStatus.ACTIVE, true, 0, null, false, null, null,
                null, null, null, null, null, null, null, null);
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.of(social));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("탈퇴한 계정은 AU_001로 막는다")
    void withdrawnAccountIsRejected() {
        Account withdrawn = Account.reconstitute(1L, EMAIL, HASH, Role.FREELANCER, "홍길동", "01012345678",
                SignupType.EMAIL, AccountStatus.WITHDRAWN, true, 0, null, false, null, null,
                null, LocalDateTime.now(), null, null, null, null, null, null);
        given(accountQueryUseCase.findByEmailAndRole(EMAIL, Role.FREELANCER)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> loginService.login(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }
}
