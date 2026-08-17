package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.infrastructure.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository springDataRepository;
    private final AccountMapper accountMapper;

    @Override
    public Account save(Account account) {
        AccountJpaEntity saved = springDataRepository.save(accountMapper.toJpaEntity(account));
        return accountMapper.toDomain(saved);
    }

    @Override
    public Optional<Account> findById(Long id) {
        return springDataRepository.findById(id).map(accountMapper::toDomain);
    }

    @Override
    public List<Account> findByIdIn(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // JpaRepository 가 이미 제공하는 findAllById 를 쓴다. SpringDataAccountRepository 에
        // 새 메서드를 추가할 필요가 없다.
        return springDataRepository.findAllById(ids).stream()
                .map(accountMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Account> findByEmailAndRole(String email, Role role) {
        return springDataRepository.findByEmailAndRoleAndDeletedAtIsNull(email, role)
                .map(accountMapper::toDomain);
    }

    @Override
    public boolean existsByEmailAndRole(String email, Role role) {
        return springDataRepository.existsByEmailAndRole(email, role);
    }

    @Override
    public boolean existsByPhoneAndRole(String phone, Role role) {
        return springDataRepository.existsByPhoneAndRole(phone, role);
    }

    @Override
    public List<Account> findAllByNameAndPhone(String name, String phone) {
        return springDataRepository.findAllByNameAndPhoneAndDeletedAtIsNull(name, phone).stream()
                .map(accountMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Account> findByEmailAndRoleAndNameAndPhone(String email, Role role, String name, String phone) {
        return springDataRepository
                .findByEmailAndRoleAndNameAndPhoneAndDeletedAtIsNull(email, role, name, phone)
                .map(accountMapper::toDomain);
    }

    @Override
    public boolean existsRejoinRestrictedByEmailHash(String emailHash, Role role, LocalDateTime now) {
        return springDataRepository.existsByEmailHashAndRoleAndRejoinAvailableAtAfter(emailHash, role, now);
    }

    @Override
    public boolean existsRejoinRestrictedByPhoneHash(String phoneHash, Role role, LocalDateTime now) {
        return springDataRepository.existsByPhoneHashAndRoleAndRejoinAvailableAtAfter(phoneHash, role, now);
    }

    @Override
    public List<Long> findActiveIdsByRole(Role role, Long afterId, int limit) {
        return springDataRepository.findActiveIdsByRole(role, afterId, PageRequest.of(0, limit));
    }

    @Override
    public List<Account> findPurgeTargets(LocalDateTime now, int limit) {
        return springDataRepository
                .findByPurgeAtBeforeOrderByPurgeAtAsc(now, PageRequest.of(0, limit)).stream()
                .map(accountMapper::toDomain)
                .toList();
    }
}
