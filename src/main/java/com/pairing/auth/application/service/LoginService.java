package com.pairing.auth.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.command.LoginCommand;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.auth.application.port.LoginAttemptPort;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.application.usecase.LoginUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이메일 로그인.
 *
 * <p>실패 사유(없는 계정 / 비밀번호 불일치 / 역할 불일치)를 구분해 응답하지 않는다.
 * 구분해서 알려주면 그 자체가 가입 여부 확인 수단이 된다.
 *
 * <p>두 가지 제한이 함께 걸린다.
 * <ul>
 *   <li>계정 단위: 비밀번호 5회 실패 시 잠금 (이메일 인증으로 해제)</li>
 *   <li>IP 단위: 1시간 20회 실패 시 2시간 차단</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final LoginAttemptPort loginAttemptPort;
    private final AccountSuspensionPort accountSuspensionPort;
    private final AuthTokenIssuer authTokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final AuthSettings authSettings;

    @Override
    public LoginResult login(LoginCommand command) {
        String clientIp = command.clientIp();

        if (loginAttemptPort.isBlocked(clientIp)) {
            throw new BusinessException(AuthErrorCode.LOGIN_BLOCKED);
        }

        String email = ContactPolicy.normalizeEmail(command.email());

        // 이메일은 역할별 유니크라 (이메일, 역할)로 조회해야 계정 하나로 좁혀진다.
        // 탭을 잘못 고른 경우도 여기서 "계정 없음"이 되어 AU_001로 나간다.
        Optional<Account> found = accountQueryUseCase.findByEmailAndRole(email, command.role());

        if (found.isEmpty()) {
            recordIpFailure(clientIp);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        Account account = found.get();

        if (account.isWithdrawn()) {
            recordIpFailure(clientIp);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        if (account.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (accountSuspensionPort.isSuspended(account.getId())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }

        // 소셜 전용 계정은 비밀번호가 없다. 여기서 걸러내지 않으면 matches()가 NPE를 낸다.
        if (account.isSocialOnly()) {
            recordIpFailure(clientIp);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(command.password(), account.getPasswordHash())) {
            recordIpFailure(clientIp);

            Account updated = accountCommandUseCase.applyLoginFailure(
                    account.getId(), authSettings.getLoginFailMax());

            if (updated.isLocked()) {
                throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        loginAttemptPort.clearFailure(clientIp);
        Account loggedIn = accountCommandUseCase.applyLoginSuccess(account.getId());

        return authTokenIssuer.issue(loggedIn);
    }

    private void recordIpFailure(String clientIp) {
        loginAttemptPort.recordFailure(
                clientIp,
                authSettings.getIpFailMax(),
                authSettings.getIpFailWindow(),
                authSettings.getIpBlockDuration()
        );
    }
}
