package com.pairing.client.application.usecase;

import com.pairing.client.application.command.ClientProfileUpdateCommand;
import com.pairing.client.application.result.ClientMyPageResult;

public interface ClientCommandUseCase {

    ClientMyPageResult updateMyPage(ClientProfileUpdateCommand command);
}
