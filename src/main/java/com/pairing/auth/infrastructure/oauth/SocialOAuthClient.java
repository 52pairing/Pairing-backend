package com.pairing.auth.infrastructure.oauth;

import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.port.SocialProfile;

/** 공급자별 OAuth 연동. 새 공급자는 이 인터페이스만 구현하면 어댑터가 자동으로 인식한다. */
public interface SocialOAuthClient {

    SocialProvider provider();

    String buildAuthorizeUrl(String state);

    SocialProfile fetchProfile(String code);
}
