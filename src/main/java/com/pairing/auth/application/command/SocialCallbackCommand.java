package com.pairing.auth.application.command;

import com.pairing.account.domain.model.SocialProvider;

public record SocialCallbackCommand(
        SocialProvider provider,
        String code,
        String state
) {
}
