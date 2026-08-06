package com.pairing.account.application.service;

import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.account.domain.repository.SocialAccountRepository;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 생성과 상태 전이를 담당한다.
 *
 * <p>가입은 계정과 프로필이 한 트랜잭션에서 만들어져야 한다. 중간에 실패하면
 * 로그인은 되는데 프로필이 없는 계정이 남는다.
 *
 * <p>결제수단(카드/계좌)은 가입에서 받지 않는다. 로그인 후 마이페이지에서 등록한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountCommandService implements AccountCommandUseCase {

    private final AccountRepository accountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;

    @Override
    public Long createClientAccount(CreateClientAccountCommand command) {
        Account account = accountRepository.save(Account.createByEmail(
                command.email(),
                command.passwordHash(),
                Role.CLIENT,
                command.name(),
                command.phone()
        ));

        clientProfileRepository.save(ClientProfile.create(
                account.getId(),
                command.companyName(),
                command.businessNo(),
                command.businessField(),
                command.employeeCount()
        ));

        return account.getId();
    }

    @Override
    public Long createFreelancerAccount(CreateFreelancerAccountCommand command) {
        Account account = accountRepository.save(Account.createByEmail(
                command.email(),
                command.passwordHash(),
                Role.FREELANCER,
                command.name(),
                command.phone()
        ));

        freelancerProfileRepository.save(FreelancerProfile.create(account.getId(), command.birthDate()));

        return account.getId();
    }

    @Override
    public Long createSocialFreelancerAccount(CreateSocialFreelancerAccountCommand command) {
        Account account = accountRepository.save(Account.createBySocial(
                command.email(),
                Role.FREELANCER,
                command.name(),
                command.phone(),
                command.providerEmailVerified()
        ));

        socialAccountRepository.save(SocialAccount.create(
                account.getId(),
                command.provider(),
                command.providerUid(),
                command.providerEmail(),
                command.providerEmailVerified()
        ));

        freelancerProfileRepository.save(FreelancerProfile.create(account.getId(), command.birthDate()));

        return account.getId();
    }

    @Override
    public Account applyLoginSuccess(Long accountId) {
        Account account = loadAccount(accountId);
        account.recordLoginSuccess();
        return accountRepository.save(account);
    }

    /**
     * 별도 트랜잭션에서 처리한다.
     *
     * <p>호출자(로그인)는 실패를 기록한 직후 예외를 던진다. 같은 트랜잭션에 있으면 증가시킨 실패 횟수가
     * 롤백과 함께 사라져서 "5회 실패 시 잠금"이 영원히 동작하지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account applyLoginFailure(Long accountId, int lockThreshold) {
        Account account = loadAccount(accountId);
        account.recordLoginFailure(lockThreshold);
        return accountRepository.save(account);
    }

    @Override
    public void changePassword(Long accountId, String newPasswordHash, boolean temporary) {
        Account account = loadAccount(accountId);
        account.changePassword(newPasswordHash, temporary);
        accountRepository.save(account);
    }

    @Override
    public void unlock(Long accountId) {
        Account account = loadAccount(accountId);
        account.unlock();
        accountRepository.save(account);
    }

    @Override
    public void verifyEmail(Long accountId) {
        Account account = loadAccount(accountId);
        account.verifyEmail();
        accountRepository.save(account);
    }

    private Account loadAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }
}
