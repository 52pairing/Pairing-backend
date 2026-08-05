package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;

import java.util.Optional;

public interface SocialAccountRepository {

    SocialAccount save(SocialAccount socialAccount);

    Optional<SocialAccount> findByProviderAndProviderUid(SocialProvider provider, String providerUid);

    Optional<SocialAccount> findByAccountId(Long accountId);
}
