package com.pairing.auth.application.service;

import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.command.ClientSignUpCommand;
import com.pairing.auth.application.command.FreelancerSignUpCommand;
import com.pairing.auth.application.command.SocialSignUpCommand;
import com.pairing.auth.application.policy.AgePolicy;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.auth.application.policy.PasswordPolicy;
import com.pairing.auth.application.policy.PaymentPolicy;
import com.pairing.auth.application.port.SignUpTicket;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.application.usecase.SignUpUseCase;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.terms.application.command.AgreeTermsCommand;
import com.pairing.terms.application.usecase.TermsAgreementCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원가입 오케스트레이션.
 *
 * <p>계정 생성은 account 도메인이, 약관 동의 기록은 terms 도메인이 담당한다.
 * 여기서는 "가입 완료 조건"(필수 입력 + 형식 + 중복 + 이메일 인증 + 필수 약관)만 판단한다.
 *
 * <p>세 가입 경로 모두 한 트랜잭션이다. 중간에 실패하면 약관 동의가 없는 계정이 남는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SignUpService implements SignUpUseCase {

    private static final String BUSINESS_NO_PATTERN = "^\\d{10}$";

    private final AccountCommandUseCase accountCommandUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final TermsAgreementCommandUseCase termsAgreementCommandUseCase;
    private final VerifiedMarkerPort verifiedMarkerPort;
    private final SignUpTicketPort signUpTicketPort;
    private final AuthTokenIssuer authTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final AuthSettings authSettings;

    @Override
    public Long signUpClient(ClientSignUpCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());
        String phone = ContactPolicy.normalizePhone(command.phone());

        PasswordPolicy.validate(command.password());
        PasswordPolicy.validateConfirm(command.password(), command.passwordConfirm());
        validateAgreementsPresent(command.agreements());

        // 요청 DTO의 @Pattern과 중복이지만, 커맨드가 다른 경로로 들어와도 형식이 깨지지 않게 한 번 더 본다.
        // 국세청 진위확인 API는 도입 전이라 형식 검사까지만 한다.
        if (command.businessNo() == null || !command.businessNo().matches(BUSINESS_NO_PATTERN)) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        requireEmailVerified(email);
        requireNotDuplicated(email, phone, Role.CLIENT);
        if (accountQueryUseCase.isBusinessNoDuplicated(command.businessNo())) {
            throw new BusinessException(AuthErrorCode.DUPLICATED_BUSINESS_NO);
        }
        requireRejoinAllowed(email, phone, Role.CLIENT);

        Long accountId = accountCommandUseCase.createClientAccount(new CreateClientAccountCommand(
                email,
                passwordEncoder.encode(command.password()),
                command.name(),
                phone,
                command.companyName(),
                command.businessNo(),
                command.businessField(),
                command.employeeCount(),
                PaymentPolicy.normalize(command.card()),
                PaymentPolicy.normalize(command.bankAccount())
        ));

        agreeTerms(accountId, Role.CLIENT, command.agreements(), command.userAgent());
        verifiedMarkerPort.clear(email, VerificationPurpose.SIGNUP);

        return accountId;
    }

    @Override
    public Long signUpFreelancer(FreelancerSignUpCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());
        String phone = ContactPolicy.normalizePhone(command.phone());

        PasswordPolicy.validate(command.password());
        PasswordPolicy.validateConfirm(command.password(), command.passwordConfirm());
        AgePolicy.validate(command.birthDate(), authSettings.getMinimumAge());
        validateAgreementsPresent(command.agreements());

        requireEmailVerified(email);
        requireNotDuplicated(email, phone, Role.FREELANCER);
        requireRejoinAllowed(email, phone, Role.FREELANCER);

        Long accountId = accountCommandUseCase.createFreelancerAccount(new CreateFreelancerAccountCommand(
                email,
                passwordEncoder.encode(command.password()),
                command.name(),
                phone,
                command.birthDate(),
                PaymentPolicy.normalize(command.card()),
                PaymentPolicy.normalize(command.bankAccount())
        ));

        agreeTerms(accountId, Role.FREELANCER, command.agreements(), command.userAgent());
        verifiedMarkerPort.clear(email, VerificationPurpose.SIGNUP);

        return accountId;
    }

    @Override
    public LoginResult signUpFreelancerBySocial(SocialSignUpCommand command) {
        SignUpTicket ticket = signUpTicketPort.find(command.signUpTicket())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SIGNUP_TICKET_EXPIRED));

        String email = ContactPolicy.normalizeEmail(ticket.email());
        String phone = ContactPolicy.normalizePhone(command.phone());

        AgePolicy.validate(command.birthDate(), authSettings.getMinimumAge());
        validateAgreementsPresent(command.agreements());

        // 소셜 가입에는 이메일 인증코드 단계가 없다. 공급자 인증이 그 자리를 대신한다.
        requireNotDuplicated(email, phone, Role.FREELANCER);
        requireRejoinAllowed(email, phone, Role.FREELANCER);

        if (accountQueryUseCase.findSocialAccount(ticket.provider(), ticket.providerUid()).isPresent()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_ALREADY_LINKED);
        }

        Long accountId = accountCommandUseCase.createSocialFreelancerAccount(
                new CreateSocialFreelancerAccountCommand(
                        email,
                        command.name(),
                        phone,
                        command.birthDate(),
                        ticket.provider(),
                        ticket.providerUid(),
                        ticket.email(),
                        ticket.emailVerified(),
                        PaymentPolicy.normalize(command.card()),
                        PaymentPolicy.normalize(command.bankAccount())
                ));

        agreeTerms(accountId, Role.FREELANCER, command.agreements(), command.userAgent());
        signUpTicketPort.delete(command.signUpTicket());

        Account account = accountQueryUseCase.getById(accountId);
        return authTokenIssuer.issue(account);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailDuplicated(String email, Role role) {
        return accountQueryUseCase.isEmailDuplicated(ContactPolicy.normalizeEmail(email), role);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPhoneDuplicated(String phone, Role role) {
        return accountQueryUseCase.isPhoneDuplicated(ContactPolicy.normalizePhone(phone), role);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBusinessNoDuplicated(String businessNo) {
        return accountQueryUseCase.isBusinessNoDuplicated(businessNo);
    }

    private void requireEmailVerified(String email) {
        if (!verifiedMarkerPort.isVerified(email, VerificationPurpose.SIGNUP)) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    // 같은 역할 안에서만 중복을 본다. 클라이언트로 가입한 이메일로 프리랜서 가입은 가능하고,
    // 같은 역할 안에서는 소셜과 일반 사이에도 중복이 허용되지 않는다.
    private void requireNotDuplicated(String email, String phone, Role role) {
        if (accountQueryUseCase.isEmailDuplicated(email, role)) {
            throw new BusinessException(AuthErrorCode.DUPLICATED_EMAIL);
        }
        if (accountQueryUseCase.isPhoneDuplicated(phone, role)) {
            throw new BusinessException(AuthErrorCode.DUPLICATED_PHONE);
        }
    }

    private void requireRejoinAllowed(String email, String phone, Role role) {
        boolean restricted = accountQueryUseCase.isRejoinRestricted(
                ContactPolicy.sha256(email),
                ContactPolicy.sha256(phone),
                role
        );
        if (restricted) {
            throw new BusinessException(AuthErrorCode.REJOIN_RESTRICTED);
        }
    }

    private void validateAgreementsPresent(List<AgreeTermsCommand> agreements) {
        if (agreements == null || agreements.isEmpty()) {
            throw new BusinessException(AuthErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }

    // 필수 약관 누락 판정은 terms 도메인이 한다. (TM_002)
    private void agreeTerms(Long accountId, Role role, List<AgreeTermsCommand> agreements, String userAgent) {
        termsAgreementCommandUseCase.agreeAll(accountId, role.name(), agreements, userAgent);
    }
}
