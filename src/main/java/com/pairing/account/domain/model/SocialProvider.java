package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 소셜 로그인 공급자. 프리랜서만 사용한다. */
@Getter
@RequiredArgsConstructor
public enum SocialProvider {

    KAKAO("카카오"),
    GOOGLE("구글");

    private final String label;
}
