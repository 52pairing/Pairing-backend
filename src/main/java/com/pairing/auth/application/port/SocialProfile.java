package com.pairing.auth.application.port;

import com.pairing.account.domain.model.SocialProvider;

/** 공급자에게서 받은 사용자 정보. */
public record SocialProfile(
        SocialProvider provider,
        String providerUid,
        String email,
        boolean emailVerified,
        String name
) {
}
