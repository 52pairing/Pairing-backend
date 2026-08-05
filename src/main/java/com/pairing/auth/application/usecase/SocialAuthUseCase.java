package com.pairing.auth.application.usecase;

import com.pairing.account.domain.model.SocialProvider;
import com.pairing.auth.application.command.SocialCallbackCommand;
import com.pairing.auth.application.result.AuthorizeUrlResult;
import com.pairing.auth.application.result.SocialAuthResult;

public interface SocialAuthUseCase {

    AuthorizeUrlResult authorizeUrl(SocialProvider provider, String returnUrl);

    SocialAuthResult callback(SocialCallbackCommand command);
}
