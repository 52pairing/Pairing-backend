package com.pairing.auth.application.command;

import com.pairing.auth.domain.model.VerificationPurpose;

public record SendCodeCommand(
        String email,
        VerificationPurpose purpose
) {
}
