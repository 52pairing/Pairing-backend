package com.pairing.auth.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.command.SocialCallbackCommand;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.auth.application.port.AccountSuspensionPort;
import com.pairing.auth.application.port.OAuthStatePort;
import com.pairing.auth.application.port.SignUpTicket;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.application.port.SocialProfile;
import com.pairing.auth.application.port.SocialProfileProviderPort;
import com.pairing.auth.application.result.AuthorizeUrlResult;
import com.pairing.auth.application.result.SocialAuthResult;
import com.pairing.auth.application.usecase.SocialAuthUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 로그인 (프리랜서 전용).
 *
 * <p>이미 연동된 계정이면 바로 로그인시키고, 아니면 가입 티켓을 발급해 추가 정보 입력으로 보낸다.
 * 공급자 이메일이 이미 일반 가입에 쓰였다면 요구사항(소셜↔일반 이메일 중복 불가)에 따라 막는다.
 *
 * <p>클래스 트랜잭션을 걸지 않는다. 공급자 HTTP 호출이 포함되어 있어, 트랜잭션 안에 두면
 * 네트워크가 느릴 때 DB 커넥션을 그대로 붙잡고 있게 된다. DB 쓰기는 호출하는 UseCase 쪽에서 각자 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService implements SocialAuthUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final SocialProfileProviderPort socialProfileProviderPort;
    private final OAuthStatePort oAuthStatePort;
    private final SignUpTicketPort signUpTicketPort;
    private final AccountSuspensionPort accountSuspensionPort;
    private final AuthTokenIssuer authTokenIssuer;
    private final AuthSettings authSettings;

    @Override
    public AuthorizeUrlResult authorizeUrl(SocialProvider provider, String returnUrl) {
        if (provider == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        // state는 콜백에서 한 번만 소비된다. 없으면 제3자가 만든 콜백 요청을 그대로 처리하게 된다.
        String state = UUID.randomUUID().toString();
        oAuthStatePort.save(state, returnUrl, authSettings.getOauthStateTtl());

        return new AuthorizeUrlResult(socialProfileProviderPort.buildAuthorizeUrl(provider, state), state);
    }

    @Override
    public SocialAuthResult callback(SocialCallbackCommand command) {
        if (!oAuthStatePort.consume(command.state())) {
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }

        SocialProfile profile = socialProfileProviderPort.fetchProfile(command.provider(), command.code());
        String email = ContactPolicy.normalizeEmail(profile.email());

        Optional<SocialAccount> linked =
                accountQueryUseCase.findSocialAccount(profile.provider(), profile.providerUid());

        if (linked.isPresent()) {
            Account account = accountQueryUseCase.getById(linked.get().getAccountId());
            validateLoginable(account);
            return SocialAuthResult.login(authTokenIssuer.issue(account));
        }

        // 연동 이력이 없는데 같은 이메일의 프리랜서 계정이 있으면 일반 가입으로 이미 쓰인 이메일이다.
        // 클라이언트 계정이 같은 이메일을 쓰고 있는 것은 막지 않는다. 역할이 다르면 별개 계정이다.
        if (email != null && accountQueryUseCase.isEmailDuplicated(email, Role.FREELANCER)) {
            throw new BusinessException(AuthErrorCode.DUPLICATED_EMAIL);
        }

        String ticket = UUID.randomUUID().toString();
        signUpTicketPort.save(
                ticket,
                new SignUpTicket(profile.provider(), profile.providerUid(), email,
                        profile.emailVerified(), profile.name()),
                authSettings.getSignupTicketTtl()
        );

        return SocialAuthResult.signUpRequired(ticket, email, profile.name());
    }

    private void validateLoginable(Account account) {
        if (account.isWithdrawn()) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        if (account.isLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (accountSuspensionPort.isSuspended(account.getId())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED);
        }
    }
}
