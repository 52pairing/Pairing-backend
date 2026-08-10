package com.pairing.account.application.service;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.BankCode;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.account.domain.repository.PaymentMethodRepository;
import com.pairing.account.domain.repository.SocialAccountRepository;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.auth.application.policy.ContactPolicy;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.port.out.DataEncryptionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 계정 생성과 상태 전이를 담당한다.
 *
 * <p>가입은 계정 + 프로필 + 결제수단이 한 트랜잭션에서 만들어져야 한다. 중간에 실패하면
 * 로그인은 되는데 프로필이나 정산 수단이 없는 계정이 남는다.
 *
 * <p>카드번호·계좌번호 평문은 이 클래스 밖으로 나가지 않는다. 저장 직전에 암호화한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountCommandService implements AccountCommandUseCase {

    private static final int CARD_LAST4_LENGTH = 4;

    private final AccountRepository accountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final DataEncryptionPort dataEncryptionPort;

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
                command.employeeCount(),
                command.address()
        ));

        savePaymentMethods(account.getId(), command.card(), command.bankAccount());

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
        savePaymentMethods(account.getId(), command.card(), command.bankAccount());

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
        savePaymentMethods(account.getId(), command.card(), command.bankAccount());

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

    @Override
    public void updateClientProfile(Long accountId, String companyName, EmployeeCount employeeCount, String phone,
                                    String address) {
        Account account = loadAccount(accountId);
        account.updatePhone(ContactPolicy.normalizePhone(phone));
        accountRepository.save(account);

        ClientProfile profile = clientProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        profile.updateCompanyInfo(companyName, employeeCount, address);
        clientProfileRepository.save(profile);
    }

    @Override
    public void updateFreelancerMatchingSettings(Long accountId, boolean aiMatchingAgreed, boolean matchingPaused) {
        FreelancerProfile profile = freelancerProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        profile.updateMatchingSettings(aiMatchingAgreed, matchingPaused);
        freelancerProfileRepository.save(profile);
    }

    @Override
    public void updateFreelancerProfile(Long accountId, String phone, String address, Long profileFileId,
                                        boolean aiMatchingAgreed) {
        Account account = loadAccount(accountId);
        account.updatePhone(ContactPolicy.normalizePhone(phone));
        accountRepository.save(account);

        FreelancerProfile profile = freelancerProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        profile.updateProfile(address, profileFileId, aiMatchingAgreed);
        freelancerProfileRepository.save(profile);
    }

    @Override
    public PaymentMethod updateCard(Long accountId, CardCommand command) {
        PaymentMethod card = loadPaymentMethod(accountId, PaymentMethodType.CARD);
        String cardNumber = normalizeNumber(command.cardNumber());

        card.updateCard(dataEncryptionPort.encrypt(cardNumber), command.cardBrand(), lastFourOf(cardNumber),
                command.cardHolder());
        return paymentMethodRepository.save(card);
    }

    @Override
    public PaymentMethod updateBankAccount(Long accountId, BankAccountCommand command) {
        PaymentMethod bankAccount = loadPaymentMethod(accountId, PaymentMethodType.BANK_ACCOUNT);

        // 알 수 없는 은행 코드가 들어오면 나중에 이체 단계에서야 터진다. 저장 전에 막는다.
        BankCode bank = BankCode.find(command.bankCode())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.UNKNOWN_BANK_CODE));
        String accountNo = normalizeNumber(command.accountNo());

        bankAccount.updateBankAccount(bank.getCode(), dataEncryptionPort.encrypt(accountNo),
                lastFourOf(accountNo), command.accountHolder());
        return paymentMethodRepository.save(bankAccount);
    }

    private PaymentMethod loadPaymentMethod(Long accountId, PaymentMethodType methodType) {
        return paymentMethodRepository.findAllByAccountId(accountId).stream()
                .filter(paymentMethod -> paymentMethod.getMethodType() == methodType)
                .findFirst()
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PAYMENT_METHOD_NOT_FOUND));
    }

    private Account loadAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }

    /**
     * 카드와 계좌를 한 번에 저장한다.
     *
     * <p>수수료는 카드로 결제하고 용역비는 계좌로 받으므로 둘 다 필요하다.
     * 평문 번호는 여기서 암호문으로 바뀌고, 카드 끝 4자리만 화면 표시용으로 따로 남긴다.
     */
    private void savePaymentMethods(Long accountId, CardCommand card, BankAccountCommand bankAccount) {
        if (card == null || bankAccount == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }

        String cardNumber = normalizeNumber(card.cardNumber());
        String accountNo = normalizeNumber(bankAccount.accountNo());

        // 알 수 없는 은행 코드가 들어오면 나중에 이체 단계에서야 터진다. 저장 전에 막는다.
        BankCode bank = BankCode.find(bankAccount.bankCode())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.UNKNOWN_BANK_CODE));

        paymentMethodRepository.saveAll(List.of(
                PaymentMethod.createCard(
                        accountId,
                        dataEncryptionPort.encrypt(cardNumber),
                        card.cardBrand(),
                        lastFourOf(cardNumber)),
                PaymentMethod.createBankAccount(
                        accountId,
                        bank.getCode(),
                        dataEncryptionPort.encrypt(accountNo),
                        lastFourOf(accountNo),
                        bankAccount.accountHolder())
        ));
    }

    /**
     * 카드번호·계좌번호에서 숫자만 남긴다.
     *
     * <p>화면은 하이픈을 넣어 보내고 사용자는 넣는 위치가 매번 다르다. 정규화하지 않으면 같은 카드가
     * 가입 때와 수정 때 서로 다른 암호문으로 저장된다.
     */
    private String normalizeNumber(String number) {
        if (number == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() < CARD_LAST4_LENGTH) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return digits;
    }

    /** 화면 표시용 끝 4자리. 암호문은 복호화하지 않고도 이 값으로 카드를 구분할 수 있다. */
    private String lastFourOf(String digits) {
        return digits.substring(digits.length() - CARD_LAST4_LENGTH);
    }
}
