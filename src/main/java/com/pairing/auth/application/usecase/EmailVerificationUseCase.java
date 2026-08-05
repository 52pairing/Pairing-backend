package com.pairing.auth.application.usecase;

import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.SendCodeCommand;
import com.pairing.auth.application.result.SendCodeResult;

public interface EmailVerificationUseCase {

    SendCodeResult send(SendCodeCommand command);

    void confirm(ConfirmCodeCommand command);
}
