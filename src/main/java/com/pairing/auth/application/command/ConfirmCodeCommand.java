package com.pairing.auth.application.command;

import com.pairing.auth.domain.model.VerificationPurpose;

public record ConfirmCodeCommand(
        String email,
        VerificationPurpose purpose,
        String code
) {
}
