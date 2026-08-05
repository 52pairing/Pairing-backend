package com.pairing.auth.application.usecase;

import com.pairing.auth.application.result.LoginResult;

public interface TokenUseCase {

    /** 리프레시 토큰으로 액세스 토큰을 재발급한다. 리프레시 토큰도 함께 회전한다. */
    LoginResult reissue(String refreshToken);

    void logout(Long accountId);
}
