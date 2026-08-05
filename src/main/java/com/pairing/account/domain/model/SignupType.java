package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SignupType {

    EMAIL("이메일"),
    SOCIAL("소셜");

    private final String label;
}
