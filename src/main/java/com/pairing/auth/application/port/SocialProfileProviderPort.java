package com.pairing.auth.application.port;

import com.pairing.account.domain.model.SocialProvider;

/** 소셜 공급자 연동. 인가 URL 생성과 code 교환을 담당한다. */
public interface SocialProfileProviderPort {

    String buildAuthorizeUrl(SocialProvider provider, String state);

    /** 인가 코드를 액세스 토큰으로 바꾸고 사용자 정보를 가져온다. */
    SocialProfile fetchProfile(SocialProvider provider, String code);
}
