package com.pairing.client.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.client.application.command.ClientProfileUpdateCommand;
import com.pairing.client.application.result.ClientMyPageResult;
import com.pairing.client.application.usecase.ClientCommandUseCase;
import com.pairing.client.application.usecase.ClientQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientCommandService implements ClientCommandUseCase {

    private final AccountCommandUseCase accountCommandUseCase;
    private final ClientQueryUseCase clientQueryUseCase;

    @Override
    public ClientMyPageResult updateMyPage(ClientProfileUpdateCommand command) {
        accountCommandUseCase.updateClientProfile(
                command.accountId(), command.companyName(), command.employeeCount(), command.phone(),
                command.address());
        return clientQueryUseCase.findMyPage(command.accountId());
    }
}
