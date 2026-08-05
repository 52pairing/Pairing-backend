package com.pairing.auth.application.usecase;

import com.pairing.auth.application.command.LoginCommand;
import com.pairing.auth.application.result.LoginResult;

public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
