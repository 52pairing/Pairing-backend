package com.pairing.auth.application.port;

import com.pairing.account.domain.model.SocialProvider;

/**
 * 소셜 인증은 끝났지만 아직 가입은 하지 않은 상태를 담는다.
 *
 * <p>이 값을 프론트에 그대로 내려주면 사용자가 이메일을 바꿔 보낼 수 있으므로,
 * 서버가 티켓 키만 발급하고 실제 값은 저장소에 둔다.
 */
public record SignUpTicket(
        SocialProvider provider,
        String providerUid,
        String email,
        boolean emailVerified,
        String name
) {
}
