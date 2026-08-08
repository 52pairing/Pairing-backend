package com.pairing.project.infrastructure;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.project.application.port.ClientProfileReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ClientProfileReaderPort 구현. account 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class ClientProfileReaderAdapter implements ClientProfileReaderPort {

    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    public ClientProfileView getByAccountId(Long accountId) {
        ClientProfile profile = accountQueryUseCase.getClientProfile(accountId);
        return new ClientProfileReaderPort.ClientProfileView(
                profile.getId(), profile.getAddress(), toGrade(profile.getGrade()));
    }

    /** grade 는 account 쪽에서 문자열로 들고 있다. 값이 이상하면 할인 없는 등급으로 본다. */
    private ClientGrade toGrade(String grade) {
        try {
            return ClientGrade.valueOf(grade);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ClientGrade.SILVER;
        }
    }
}