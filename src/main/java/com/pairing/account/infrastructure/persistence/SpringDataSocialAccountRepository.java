package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataSocialAccountRepository extends JpaRepository<SocialAccountJpaEntity, Long> {

    Optional<SocialAccountJpaEntity> findByProviderAndProviderUid(SocialProvider provider, String providerUid);

    Optional<SocialAccountJpaEntity> findByAccountId(Long accountId);
}
