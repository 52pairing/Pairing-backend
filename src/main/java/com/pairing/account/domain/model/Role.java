package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 계정 역할. JWT의 role 클레임과 ROLE_{name} 권한으로 그대로 쓰인다. */
@Getter
@RequiredArgsConstructor
public enum Role {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서"),
    ADMIN("관리자");

    private final String label;
}
