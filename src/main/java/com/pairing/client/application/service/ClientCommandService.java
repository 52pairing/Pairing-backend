package com.pairing.client.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.client.application.command.ClientProfileUpdateCommand;
import com.pairing.client.application.result.ClientMyPageResult;
import com.pairing.client.application.usecase.ClientCommandUseCase;
import com.pairing.client.application.usecase.ClientQueryUseCase;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientCommandService implements ClientCommandUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final ClientQueryUseCase clientQueryUseCase;
    private final EmailVerificationUseCase emailVerificationUseCase;

    @Override
    public ClientMyPageResult updateMyPage(ClientProfileUpdateCommand command) {
        Account account = accountQueryUseCase.getById(command.accountId());
        if (!emailVerificationUseCase.isVerified(account.getEmail(), VerificationPurpose.PROFILE_UPDATE)) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        accountCommandUseCase.updateClientProfile(
                command.accountId(), command.companyName(), command.employeeCount(), command.phone(),
                command.address(), command.logoFileId());

        ClientMyPageResult result = clientQueryUseCase.findMyPage(command.accountId());

        // 인증 마커는 1회용이다. 남겨 두면 같은 인증으로 여러 번 수정할 수 있다.
        // 소비는 맨 마지막에 한다. 조회에서 예외가 나면 저장은 롤백되는데 마커(Redis)는 롤백되지 않아,
        // 먼저 지우면 "저장은 안 됐는데 인증만 날아간" 상태가 된다.
        emailVerificationUseCase.clearVerification(account.getEmail(), VerificationPurpose.PROFILE_UPDATE);

        return result;
    }
}
