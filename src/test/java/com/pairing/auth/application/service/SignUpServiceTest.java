package com.pairing.auth.application.service;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.command.ClientSignUpCommand;
import com.pairing.auth.application.command.FreelancerSignUpCommand;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import com.pairing.terms.application.command.AgreeTermsCommand;
import com.pairing.terms.application.usecase.TermsAgreementCommandUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 가입 완료 조건 검증.
 *
 * <p>중복 판정이 역할별로 이뤄지는지, 이메일 인증 없이는 가입이 막히는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignUpServiceTest {

    private static final String EMAIL = "owner@pairing.com";
    private static final String PHONE_INPUT = "010-1234-5678";
    private static final String PHONE_STORED = "01012345678";
    private static final String PASSWORD = "Passw0rd!";
    private static final String BUSINESS_NO = "1234567890";
    private static final String ADDRESS = "서울 강남구 테헤란로 1";

    @Mock
    private AccountCommandUseCase accountCommandUseCase;
    @Mock
    private AccountQueryUseCase accountQueryUseCase;
    @Mock
    private TermsAgreementCommandUseCase termsAgreementCommandUseCase;
    @Mock
    private VerifiedMarkerPort verifiedMarkerPort;
    @Mock
    private SignUpTicketPort signUpTicketPort;
    @Mock
    private AuthTokenIssuer authTokenIssuer;
    @Mock
    private PasswordEncoder passwordEncoder;

    private SignUpService signUpService;

    @BeforeEach
    void setUp() {
        signUpService = new SignUpService(accountCommandUseCase, accountQueryUseCase,
                termsAgreementCommandUseCase, verifiedMarkerPort, signUpTicketPort, authTokenIssuer,
                passwordEncoder, new AuthSettings());
    }

    private CardCommand card() {
        return new CardCommand("1234-5678-1234-5678", "신한카드", null);
    }

    private BankAccountCommand bankAccount() {
        return new BankAccountCommand("088", "110-123-456789", "홍길동");
    }

    private List<AgreeTermsCommand> agreements() {
        return List.of(new AgreeTermsCommand(1L, true));
    }

    private ClientSignUpCommand clientCommand() {
        return new ClientSignUpCommand(EMAIL, PASSWORD, PASSWORD, "홍길동", PHONE_INPUT,
                "주식회사 페어링", BUSINESS_NO, BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_10_49,
                ADDRESS, card(), bankAccount(), agreements(), "JUnit");
    }

    private FreelancerSignUpCommand freelancerCommand(LocalDate birthDate) {
        return new FreelancerSignUpCommand(EMAIL, PASSWORD, PASSWORD, "홍길동", PHONE_INPUT, birthDate,
                card(), bankAccount(), agreements(), "JUnit");
    }

    @Test
    @DisplayName("클라이언트 가입은 정규화된 값으로 계정을 만들고 약관 동의를 기록한다")
    void signUpClientSuccess() {
        given(verifiedMarkerPort.isVerified(EMAIL, VerificationPurpose.SIGNUP)).willReturn(true);
        given(passwordEncoder.encode(PASSWORD)).willReturn("$2a$10$hash");
        given(accountCommandUseCase.createClientAccount(any())).willReturn(1L);

        Long accountId = signUpService.signUpClient(clientCommand());

        assertThat(accountId).isEqualTo(1L);
        verify(accountCommandUseCase).createClientAccount(org.mockito.ArgumentMatchers.argThat(command ->
                command.email().equals(EMAIL)
                        && command.phone().equals(PHONE_STORED)
                        && command.passwordHash().equals("$2a$10$hash")));
        // 하이픈이 섞여 들어와도 숫자만 저장되도록 정규화한다.
        verify(accountCommandUseCase).createClientAccount(org.mockito.ArgumentMatchers.argThat(command ->
                command.card().cardNumber().equals("1234567812345678")
                        && command.bankAccount().accountNo().equals("110123456789")
                        && command.bankAccount().bankCode().equals("088")));
        verify(termsAgreementCommandUseCase).agreeAll(eq(1L), eq("CLIENT"), any(), eq("JUnit"));
        // 재사용을 막기 위해 인증 마커는 가입 직후 지운다.
        verify(verifiedMarkerPort).clear(EMAIL, VerificationPurpose.SIGNUP);
    }

    @Test
    @DisplayName("이메일 인증을 마치지 않으면 AU_006으로 막는다")
    void signUpRequiresEmailVerification() {
        given(verifiedMarkerPort.isVerified(anyString(), any())).willReturn(false);

        assertThatThrownBy(() -> signUpService.signUpClient(clientCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);

        verify(accountCommandUseCase, never()).createClientAccount(any());
    }

    @Test
    @DisplayName("중복 판정은 가입하려는 역할 기준으로만 한다")
    void duplicationIsCheckedPerRole() {
        given(verifiedMarkerPort.isVerified(EMAIL, VerificationPurpose.SIGNUP)).willReturn(true);
        given(accountQueryUseCase.isEmailDuplicated(EMAIL, Role.CLIENT)).willReturn(true);

        assertThatThrownBy(() -> signUpService.signUpClient(clientCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.DUPLICATED_EMAIL);

        // 프리랜서 쪽 중복 여부는 클라이언트 가입 판단에 쓰이지 않는다.
        verify(accountQueryUseCase, never()).isEmailDuplicated(EMAIL, Role.FREELANCER);
    }

    @Test
    @DisplayName("휴대폰 중복은 AU_008로 구분해서 알려준다")
    void duplicatedPhone() {
        given(verifiedMarkerPort.isVerified(EMAIL, VerificationPurpose.SIGNUP)).willReturn(true);
        given(accountQueryUseCase.isPhoneDuplicated(PHONE_STORED, Role.CLIENT)).willReturn(true);

        assertThatThrownBy(() -> signUpService.signUpClient(clientCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.DUPLICATED_PHONE);
    }

    @Test
    @DisplayName("사업자등록번호가 이미 등록됐으면 AU_009로 막는다")
    void duplicatedBusinessNo() {
        given(verifiedMarkerPort.isVerified(EMAIL, VerificationPurpose.SIGNUP)).willReturn(true);
        given(accountQueryUseCase.isBusinessNoDuplicated(BUSINESS_NO)).willReturn(true);

        assertThatThrownBy(() -> signUpService.signUpClient(clientCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.DUPLICATED_BUSINESS_NO);
    }

    @Test
    @DisplayName("탈퇴 후 30일이 지나지 않았으면 AU_021로 막는다")
    void rejoinRestricted() {
        given(verifiedMarkerPort.isVerified(EMAIL, VerificationPurpose.SIGNUP)).willReturn(true);
        given(accountQueryUseCase.isRejoinRestricted(anyString(), anyString(), eq(Role.CLIENT))).willReturn(true);

        assertThatThrownBy(() -> signUpService.signUpClient(clientCommand()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REJOIN_RESTRICTED);
    }

    @Test
    @DisplayName("만 18세 미만 프리랜서는 가입할 수 없다")
    void underageFreelancerRejected() {
        LocalDate underage = LocalDate.now().minusYears(18).plusDays(1);

        assertThatThrownBy(() -> signUpService.signUpFreelancer(freelancerCommand(underage)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNDER_MINIMUM_AGE);

        verify(accountCommandUseCase, never()).createFreelancerAccount(any());
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 계정을 만들지 않는다")
    void passwordConfirmMismatch() {
        ClientSignUpCommand command = new ClientSignUpCommand(EMAIL, PASSWORD, "Different1!", "홍길동",
                PHONE_INPUT, "주식회사 페어링", BUSINESS_NO, BusinessField.IT_CONTENTS_AI,
                EmployeeCount.SIZE_10_49, ADDRESS, card(), bankAccount(), agreements(), "JUnit");

        assertThatThrownBy(() -> signUpService.signUpClient(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }

    @Test
    @DisplayName("가입 티켓이 만료됐으면 소셜 가입을 진행하지 않는다")
    void expiredSignUpTicket() {
        given(signUpTicketPort.find(anyString())).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> signUpService.signUpFreelancerBySocial(
                new com.pairing.auth.application.command.SocialSignUpCommand(
                        "expired-ticket", "홍길동", PHONE_INPUT, LocalDate.of(1995, 3, 1),
                        card(), bankAccount(), agreements(), "JUnit")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SIGNUP_TICKET_EXPIRED);
    }
}
