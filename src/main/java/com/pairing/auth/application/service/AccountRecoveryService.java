package com.pairing.auth.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.command.ChangePasswordCommand;
import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.FindEmailCommand;
import com.pairing.auth.application.command.PasswordResetRequestCommand;
import com.pairing.auth.application.command.UnlockCommand;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.auth.application.policy.EmailMaskingPolicy;
import com.pairing.auth.application.policy.PasswordPolicy;
import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.application.port.PasswordResetTokenPort;
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.auth.application.port.VerifiedMarkerPort;
import com.pairing.auth.application.usecase.AccountRecoveryUseCase;
import com.pairing.auth.application.result.MaskedEmailResult;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 아이디 찾기 / 비밀번호 재설정 / 잠금 해제.
 *
 * <p>비밀번호 재설정은 링크 방식이다. 3분 안에 링크를 열면 임시 비밀번호를 발급하고,
 * 사용자는 그 비밀번호로 로그인한 뒤 새 비밀번호를 등록한다. (요구사항 R14)
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountRecoveryService implements AccountRecoveryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final EmailVerificationUseCase emailVerificationUseCase;
    private final PasswordResetTokenPort passwordResetTokenPort;
    private final TokenStorePort tokenStorePort;
    private final SessionRegistryPort sessionRegistryPort;
    private final VerifiedMarkerPort verifiedMarkerPort;
    private final MailSenderPort mailSenderPort;
    private final PasswordEncoder passwordEncoder;
    private final AuthSettings authSettings;

    @Override
    @Transactional(readOnly = true)
    public List<MaskedEmailResult> findMaskedEmails(FindEmailCommand command) {
        String phone = ContactPolicy.normalizePhone(command.phone());
        List<Account> accounts = accountQueryUseCase.findAllByNameAndPhone(command.name(), phone);

        // 두 역할로 가입했다면 두 건이 나온다. 어느 탭으로 로그인할지 사용자가 골라야 하므로 역할을 함께 준다.
        List<MaskedEmailResult> results = accounts.stream()
                .filter(account -> !account.isWithdrawn())
                .map(account -> new MaskedEmailResult(
                        account.getRole(), EmailMaskingPolicy.mask(account.getEmail())))
                .toList();

        if (results.isEmpty()) {
            throw new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND);
        }

        return results;
    }

    @Override
    public void requestPasswordReset(PasswordResetRequestCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());
        String phone = ContactPolicy.normalizePhone(command.phone());

        Optional<Account> found = accountQueryUseCase.findByEmailAndRoleAndNameAndPhone(
                email, command.role(), command.name(), phone);

        // 일치하지 않아도 같은 응답을 준다. 404를 주면 "그 이메일은 가입돼 있다"는 신호가 된다.
        if (found.isEmpty()) {
            log.info("비밀번호 재설정 요청이 회원 정보와 일치하지 않는다.");
            return;
        }

        Account account = found.get();

        // 소셜 전용 계정은 비밀번호가 없어 재설정 대상이 아니다.
        if (account.isSocialOnly()) {
            log.info("소셜 전용 계정에 대한 비밀번호 재설정 요청이다. accountId={}", account.getId());
            return;
        }

        String token = UUID.randomUUID().toString();
        passwordResetTokenPort.save(token, account.getId(), authSettings.getPasswordResetTtl());

        mailSenderPort.send(
                account.getEmail(),
                "[페어링] 비밀번호 재설정 안내",
                """
                        아래 링크로 %d분 안에 접속하면 임시 비밀번호를 발급해 드립니다.

                        %s/reset-password?token=%s

                        본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다."""
                        .formatted(authSettings.getPasswordResetTtl().toMinutes(),
                                authSettings.getFrontBaseUrl(), token)
        );
    }

    @Override
    public void issueTempPassword(String resetToken) {
        Long accountId = passwordResetTokenPort.findAccountId(resetToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        Account account = accountQueryUseCase.getById(accountId);
        String tempPassword = PasswordPolicy.generateTemporary();

        accountCommandUseCase.changePassword(accountId, passwordEncoder.encode(tempPassword), true);
        passwordResetTokenPort.delete(resetToken);

        // 재설정을 한 순간 기존 세션은 신뢰할 수 없다. 로그인 상태를 모두 끊는다.
        tokenStorePort.delete(accountId);
        sessionRegistryPort.clear(accountId);

        mailSenderPort.send(
                account.getEmail(),
                "[페어링] 임시 비밀번호 안내",
                """
                        임시 비밀번호: %s

                        임시 비밀번호로 로그인한 뒤 새 비밀번호를 등록해 주세요.
                        등록을 마치면 다시 로그인해야 합니다."""
                        .formatted(tempPassword)
        );
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        Account account = accountQueryUseCase.getById(command.accountId());

        if (account.isSocialOnly()
                || !passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        PasswordPolicy.validate(command.newPassword());
        PasswordPolicy.validateConfirm(command.newPassword(), command.newPasswordConfirm());

        if (passwordEncoder.matches(command.newPassword(), account.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        accountCommandUseCase.changePassword(
                account.getId(), passwordEncoder.encode(command.newPassword()), false);

        // 비밀번호가 바뀌면 기존 토큰은 무효로 본다. 프론트는 재로그인 화면으로 보낸다.
        tokenStorePort.delete(account.getId());
        sessionRegistryPort.clear(account.getId());
    }

    @Override
    public void unlock(UnlockCommand command) {
        String email = ContactPolicy.normalizeEmail(command.email());

        // 코드 검증 실패는 여기서 예외로 끝난다. (AU_004 / AU_005 / AU_012)
        emailVerificationUseCase.confirm(
                new ConfirmCodeCommand(email, VerificationPurpose.UNLOCK, command.code()));

        Account account = accountQueryUseCase.findByEmailAndRole(email, command.role())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        accountCommandUseCase.unlock(account.getId());
        verifiedMarkerPort.clear(email, VerificationPurpose.UNLOCK);
    }
}
