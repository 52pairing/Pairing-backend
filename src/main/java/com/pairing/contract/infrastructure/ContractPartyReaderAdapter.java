package com.pairing.contract.infrastructure;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ContractPartyReaderPort 구현. account 도메인의 인바운드 포트를 호출한다.
 *
 * <p>프리랜서 이름은 프로필이 아니라 계정에 있다. 프로필 -&gt; accountId -&gt; 계정 순으로 두 번 탄다.
 * 계약서에 이름이 비면 곤란하지만, 조회 실패로 계약 조회 전체를 막는 것도 과하다. null 을 돌려주고
 * 화면이 판단하게 둔다.
 */
@Component
@RequiredArgsConstructor
public class ContractPartyReaderAdapter implements ContractPartyReaderPort {

    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    public String findClientName(Long clientProfileId) {
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(ClientProfile::getCompanyName)
                .orElse(null);
    }

    @Override
    public String findFreelancerName(Long freelancerProfileId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .map(FreelancerProfile::getAccountId)
                .map(accountQueryUseCase::getById)
                .map(account -> account.getName())
                .orElse(null);
    }

    @Override
    public Long findFreelancerAccountId(Long freelancerProfileId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .map(FreelancerProfile::getAccountId)
                .orElse(null);
    }

    @Override
    public Long findClientAccountId(Long clientProfileId) {
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(ClientProfile::getAccountId)
                .orElse(null);
    }
}
