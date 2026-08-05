package com.pairing.auth.application.result;

import com.pairing.account.domain.model.Role;

/**
 * 아이디 찾기 결과 한 건.
 *
 * <p>같은 사람이 클라이언트와 프리랜서로 각각 가입할 수 있어 역할을 함께 내려준다.
 * 역할이 없으면 사용자가 어느 탭으로 로그인해야 하는지 알 수 없다.
 */
public record MaskedEmailResult(
        Role role,
        String maskedEmail
) {
}
