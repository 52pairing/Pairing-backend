package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 소셜 로그인 연동 정보. (provider, providerUid)가 유니크다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    private Long id;
    private Long accountId;
    private SocialProvider provider;
    private String providerUid;
    private String providerEmail;
    private boolean emailVerified;
    private LocalDateTime connectedAt;

    private SocialAccount(Long id, Long accountId, SocialProvider provider, String providerUid,
                          String providerEmail, boolean emailVerified, LocalDateTime connectedAt) {
        this.id = id;
        this.accountId = accountId;
        this.provider = provider;
        this.providerUid = providerUid;
        this.providerEmail = providerEmail;
        this.emailVerified = emailVerified;
        this.connectedAt = connectedAt;
    }

    public static SocialAccount create(Long accountId, SocialProvider provider, String providerUid,
                                       String providerEmail, boolean emailVerified) {
        if (accountId == null || provider == null || providerUid == null || providerUid.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new SocialAccount(null, accountId, provider, providerUid, providerEmail, emailVerified,
                LocalDateTime.now());
    }

    public static SocialAccount reconstitute(Long id, Long accountId, SocialProvider provider, String providerUid,
                                             String providerEmail, boolean emailVerified,
                                             LocalDateTime connectedAt) {
        return new SocialAccount(id, accountId, provider, providerUid, providerEmail, emailVerified, connectedAt);
    }
}
