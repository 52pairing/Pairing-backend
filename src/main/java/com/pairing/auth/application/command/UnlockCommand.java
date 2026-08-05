package com.pairing.auth.application.command;

import com.pairing.account.domain.model.Role;

/** 잠긴 계정을 이메일 인증으로 푼다. */
public record UnlockCommand(
        String email,
        Role role,
        String code
) {
}
