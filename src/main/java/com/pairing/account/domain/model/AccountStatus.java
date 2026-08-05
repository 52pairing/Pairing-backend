package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계정 상태.
 *
 * <p>정지(SUSPENDED)는 상태값이 아니라 Redis에서 관리한다. (스키마 v12 결정)
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus {

    PENDING("가입 대기"),
    ACTIVE("정상"),
    LOCKED("잠금"),
    WITHDRAWN("탈퇴");

    private final String label;
}
