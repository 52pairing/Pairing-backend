package com.pairing.auth.application.command;

import com.pairing.account.domain.model.Role;

/** 비밀번호 재설정 링크 요청. 이메일이 역할별 유니크라 역할이 함께 필요하다. */
public record PasswordResetRequestCommand(
        String email,
        Role role,
        String name,
        String phone
) {
}
