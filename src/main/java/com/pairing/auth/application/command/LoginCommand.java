package com.pairing.auth.application.command;

import com.pairing.account.domain.model.Role;

/** 이메일 로그인. role은 화면 탭(클라이언트/프리랜서) 값이다. */
public record LoginCommand(
        String email,
        String password,
        Role role,
        String clientIp
) {
}
