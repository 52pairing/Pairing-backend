package com.pairing.account.application.service;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.application.command.WithdrawAccountCommand;
import com.pairing.account.application.result.WithdrawalEligibilityResult;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.WithdrawalEligibilityUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Address;
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
import com.pairing.auth.application.port.SessionRegistryPort;
import com.pairing.auth.application.port.TokenStorePort;
import com.pairing.global.port.out.DataEncryptionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 계정 생성과 상태 전이를 담당한다.
 *
 * <p>가입은 계정 + 프로필 + 결제수단이 한 트랜잭션에서 만들어져야 한다. 중간에 실패하면
 * 로그인은 되는데 프로필이나 정산 수단이 없는 계정이 남는다.
 *
 * <p>카드번호·계좌번호 평문은 이 클래스 밖으로 나가지 않는다. 저장 직전에 암호화한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountCommandService implements AccountCommandUseCase {

    private static final int CARD_LAST4_LENGTH = 4;

    /** 탈퇴 후 같은 이메일·휴대폰으로 재가입할 수 없는 기간. (R31) */
    private static final int REJOIN_RESTRICT_DAYS = 30;

    /**
     * 개인정보(이메일·휴대폰 해시)를 파기하는 시점. 정책상 <b>1년</b>이다.
     *
     * <p>사용자가 남긴 기록(완료된 프로젝트·리뷰·협상 채팅)은 이 기간과 무관하게 그대로 남는다.
     * 지워지는 건 "그 계정이 누구였는지" 를 가리키는 값뿐이다. 기록까지 지우면 상대방 화면에서
     * 거래 이력이 사라진다.
     */
    private static final int PURGE_RETENTION_YEARS = 1;

    /** 파기 배치가 한 번에 처리하는 건수. 남은 건 다음 실행에서 이어서 한다. */
    private static final int PURGE_BATCH_SIZE = 500;

    private static final String WITHDRAWN_PREFIX = "withdrawn-";
    private static final String WITHDRAWN_EMAIL_DOMAIN = "@withdrawn.pairing.invalid";

    /** 탈퇴 확인 문구. 화면 안내와 같은 값이어야 한다. */
    private static final String WITHDRAW_CONFIRM_TEXT = "탈퇴하겠습니다";

    private final AccountRepository accountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final DataEncryptionPort dataEncryptionPort;
    private final WithdrawalEligibilityUseCase withdrawalEligibilityUseCase;
    private final SessionRegistryPort sessionRegistryPort;
    private final TokenStorePort tokenStorePort;

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

        freelancerProfileRepository.save(FreelancerProfile.create(account.getId(), command.birthDate(),
                command.address()));
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

        freelancerProfileRepository.save(FreelancerProfile.create(account.getId(), command.birthDate(),
                command.address()));
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
                                    Address address, Long logoFileId) {
        Account account = loadAccount(accountId);
        account.updatePhone(ContactPolicy.normalizePhone(phone));
        accountRepository.save(account);

        ClientProfile profile = clientProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
        profile.updateCompanyInfo(companyName, employeeCount, address, logoFileId);
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
    public void updateFreelancerProfile(Long accountId, String phone, Address address, Long profileFileId,
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

        card.updateCard(dataEncryptionPort.encrypt(cardNumber), brandNameOf(command), lastFourOf(cardNumber),
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

    /**
     * 카드사를 저장할 문자열로 바꾼다. 한글 카드사명이 아니라 enum 이름("SHINHAN")을 넣는다 —
     * 카드사명은 바뀔 수 있어서 이름을 저장하면 기존 행이 옛 표기로 남는다.
     */
    private String brandNameOf(CardCommand card) {
        return card.cardBrand() == null ? null : card.cardBrand().name();
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
                        brandNameOf(card),
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
     * 회원 탈퇴. (R17, R31)
     *
     * <p>순서가 중요하다. <b>거절 조건을 먼저 다 확인한 뒤에 계정을 건드린다.</b> 중간에 예외가
     * 나면 트랜잭션이 롤백되긴 하지만, 세션 파기처럼 되돌릴 수 없는 작업이 섞이면 어긋난다.
     *
     * <p>진행 중 판정은 두 방향으로 본다. 클라이언트는 자기가 등록한 프로젝트, 프리랜서는 자기가
     * 맺은 계약이다. 역할로 갈라서 하나만 보면 반대쪽이 뚫린다.
     */
    @Override
    @Transactional
    public void withdraw(WithdrawAccountCommand command) {
        Account account = accountRepository.findById(command.accountId())
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (account.isWithdrawn()) {
            throw new BusinessException(AccountErrorCode.ALREADY_WITHDRAWN);
        }

        requireConfirmText(command.confirmText());
        requireWithdrawable(account.getId());

        LocalDateTime now = LocalDateTime.now();
        account.withdraw(
                command.reason(),
                withdrawnPlaceholder(account.getId(), WITHDRAWN_EMAIL_DOMAIN),
                withdrawnPlaceholder(account.getId(), ""),
                ContactPolicy.sha256(account.getEmail()),
                ContactPolicy.sha256(account.getPhone()),
                now.plusDays(REJOIN_RESTRICT_DAYS),
                now.plusYears(PURGE_RETENTION_YEARS));

        accountRepository.save(account);

        // 저장이 끝난 뒤에 세션을 끊는다. 먼저 끊으면 저장이 실패했을 때 로그아웃만 된 상태가 된다.
        sessionRegistryPort.clear(account.getId());
        tokenStorePort.delete(account.getId());
    }

    /**
     * 등급 산정 결과 반영. (정책 P01)
     *
     * <p>프로필이 없으면 조용히 넘어간다. 가입이 중간에 끊긴 계정 하나 때문에 월간 산정 전체가
     * 멈추면 안 된다.
     */
    @Override
    @Transactional
    public boolean applyGrade(Long accountId, Role role, String gradeCode) {
        if (role == Role.CLIENT) {
            return clientProfileRepository.findByAccountId(accountId)
                    .map(profile -> {
                        boolean changed = !gradeCode.equals(profile.getGrade());
                        profile.applyGrade(gradeCode, LocalDateTime.now());
                        clientProfileRepository.save(profile);
                        return changed;
                    })
                    .orElse(false);
        }
        return freelancerProfileRepository.findByAccountId(accountId)
                .map(profile -> {
                    boolean changed = !gradeCode.equals(profile.getGrade());
                    profile.applyGrade(gradeCode, LocalDateTime.now());
                    freelancerProfileRepository.save(profile);
                    return changed;
                })
                .orElse(false);
    }

    /**
     * 보관 기한이 지난 개인정보 파기. (개인정보 보관 1년)
     *
     * <p>탈퇴 시 {@code purgeAt} 에 파기 예정일을 적어 두고, 이 배치가 그날이 지난 계정을 집어
     * 이메일·휴대폰 해시를 지운다. 파기하면 {@code purgeAt} 이 비워져 다음 배치에 다시 잡히지 않는다.
     *
     * <p>한 번에 {@link #PURGE_BATCH_SIZE} 건씩만 처리한다. 남은 건 다음 실행에서 이어서 한다 —
     * 파기가 하루 늦어지는 것보다 배치 한 번이 DB 를 오래 붙잡는 쪽이 위험하다.
     *
     * <p>한 건이 실패해도 나머지는 계속한다. 계정 하나 때문에 전체 파기가 멈추면, 그 사실을
     * 아무도 모르는 채로 보관 기한만 계속 넘어간다.
     */
    @Override
    @Transactional
    public int purgeExpiredPersonalData() {
        List<Account> targets = accountRepository.findPurgeTargets(LocalDateTime.now(), PURGE_BATCH_SIZE);
        int purged = 0;

        for (Account account : targets) {
            try {
                account.purgePersonalData();
                accountRepository.save(account);
                purged++;
            } catch (Exception e) {
                log.error("[개인정보 파기 실패] accountId={}", account.getId(), e);
            }
        }

        if (purged > 0) {
            log.info("[개인정보 파기 완료] {}건 (대상 {}건)", purged, targets.size());
        }
        return purged;
    }

    /**
     * 확인 문구 검사.
     *
     * <p>화면에서 이미 검사하지만 서버가 다시 본다. 프론트만 믿으면 API 를 직접 부르는 경로로
     * 실수든 스크립트든 계정이 지워질 수 있다. 되돌릴 수 없는 작업이라 한 번 더 막는다.
     *
     * <p>앞뒤 공백만 정리하고 그 외에는 정확히 일치해야 한다. 띄어쓰기를 허용하면
     * "일부러 타이핑하게 만든다"는 장치 자체가 의미를 잃는다.
     */
    private void requireConfirmText(String confirmText) {
        if (confirmText == null || !WITHDRAW_CONFIRM_TEXT.equals(confirmText.trim())) {
            throw new BusinessException(AccountErrorCode.WITHDRAW_CONFIRM_MISMATCH);
        }
    }

    /**
     * 아직 끝나지 않은 거래·미납이 있으면 막는다. 탈퇴하면 상대방이 진행할 방법이 없어진다.
     *
     * <p>판정은 화면이 쓰는 것과 <b>같은 것을 쓴다</b>({@code getWithdrawalEligibility}).
     * 여기서 따로 세면 "화면은 되는데 눌러보면 막히는" 상태가 생긴다.
     */
    private void requireWithdrawable(Long accountId) {
        WithdrawalEligibilityResult eligibility = withdrawalEligibilityUseCase.getWithdrawalEligibility(accountId);
        if (eligibility.withdrawable()) {
            return;
        }

        // 정산만 결제로 풀리고 나머지는 거래가 끝나야 풀린다. 사용자가 할 일이 달라서 코드를 나눈다.
        boolean settlementOnly = eligibility.blockers().stream()
                .allMatch(blocked -> blocked.blocker().isSettlement());

        throw new BusinessException(settlementOnly
                ? AccountErrorCode.WITHDRAW_BLOCKED_BY_SETTLEMENT
                : AccountErrorCode.WITHDRAW_BLOCKED_BY_PROJECT);
    }

    /**
     * 더미 이메일·휴대폰. {@code (email, role)} 과 {@code (phone, role)} 에 유니크 제약이 있어
     * 계정 id 를 섞어 서로 겹치지 않게 만든다.
     */
    private String withdrawnPlaceholder(Long accountId, String suffix) {
        return WITHDRAWN_PREFIX + accountId + suffix;
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
