package com.pairing.auth.presentation.api;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import org.springframework.security.core.Authentication;

/**
 * 인증 주체에서 계정 ID를 꺼낸다.
 *
 * <p>필터가 토큰 subject를 문자열 principal로 넣어두기 때문에, 컨트롤러마다
 * {@code Long.valueOf(authentication.getName())} 을 반복하면 형식이 어긋났을 때 500이 난다.
 */
final class AuthenticatedAccount {

    private AuthenticatedAccount() {
        throw new IllegalStateException("Utility class");
    }

    static Long idOf(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
