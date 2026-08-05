package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;
import com.pairing.account.domain.repository.SocialAccountRepository;
import com.pairing.account.infrastructure.mapper.SocialAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryAdapter implements SocialAccountRepository {

    private final SpringDataSocialAccountRepository springDataRepository;
    private final SocialAccountMapper socialAccountMapper;

    @Override
    public SocialAccount save(SocialAccount socialAccount) {
        SocialAccountJpaEntity saved = springDataRepository.save(socialAccountMapper.toJpaEntity(socialAccount));
        return socialAccountMapper.toDomain(saved);
    }

    @Override
    public Optional<SocialAccount> findByProviderAndProviderUid(SocialProvider provider, String providerUid) {
        return springDataRepository.findByProviderAndProviderUid(provider, providerUid)
                .map(socialAccountMapper::toDomain);
    }

    @Override
    public Optional<SocialAccount> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountId(accountId).map(socialAccountMapper::toDomain);
    }
}
