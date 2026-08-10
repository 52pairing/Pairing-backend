package com.pairing.account.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.account.domain.repository.PaymentMethodRepository;
import com.pairing.account.domain.repository.SocialAccountRepository;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountQueryService implements AccountQueryUseCase {

    private final AccountRepository accountRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    public Account getById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Override
    public Optional<Account> findByEmailAndRole(String email, Role role) {
        return accountRepository.findByEmailAndRole(email, role);
    }

    @Override
    public List<Account> findAllByNameAndPhone(String name, String phone) {
        return accountRepository.findAllByNameAndPhone(name, phone);
    }

    @Override
    public Optional<Account> findByEmailAndRoleAndNameAndPhone(String email, Role role, String name, String phone) {
        return accountRepository.findByEmailAndRoleAndNameAndPhone(email, role, name, phone);
    }

    @Override
    public boolean isEmailDuplicated(String email, Role role) {
        return accountRepository.existsByEmailAndRole(email, role);
    }

    @Override
    public boolean isPhoneDuplicated(String phone, Role role) {
        return accountRepository.existsByPhoneAndRole(phone, role);
    }

    @Override
    public boolean isBusinessNoDuplicated(String businessNo) {
        return clientProfileRepository.existsByBusinessNo(businessNo);
    }

    @Override
    public boolean isRejoinRestricted(String emailHash, String phoneHash, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return accountRepository.existsRejoinRestrictedByEmailHash(emailHash, role, now)
                || accountRepository.existsRejoinRestrictedByPhoneHash(phoneHash, role, now);
    }

    @Override
    public Optional<SocialAccount> findSocialAccount(SocialProvider provider, String providerUid) {
        return socialAccountRepository.findByProviderAndProviderUid(provider, providerUid);
    }

    @Override
    public ClientProfile getClientProfile(Long accountId) {
        return clientProfileRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PROFILE_NOT_FOUND));
    }

    @Override
    public Optional<FreelancerProfile> findFreelancerProfileById(Long freelancerProfileId) {
        return freelancerProfileRepository.findById(freelancerProfileId);
    }

    @Override
    public Optional<FreelancerProfile> findFreelancerProfileByAccountId(Long accountId) {
        return freelancerProfileRepository.findByAccountId(accountId);
    }

    @Override
    public List<Long> filterActiveAiMatchingAgreed(Collection<Long> accountIds) {
        // 빈 목록을 그대로 내리면 IN () 이 되어 DB 마다 동작이 갈린다. 여기서 끊는다.
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        return freelancerProfileRepository.filterActiveAiMatchingAgreed(accountIds);
    }

    @Override
    public Optional<ClientProfile> findClientProfileById(Long clientProfileId) {
        return clientProfileRepository.findById(clientProfileId);
    }

    @Override
    public List<PaymentMethod> findMyPaymentMethods(Long accountId) {
        return paymentMethodRepository.findAllByAccountId(accountId);
    }
}
