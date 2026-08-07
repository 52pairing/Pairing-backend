package com.pairing.negotiation.infrastructure.account;

import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 신원 매핑 포트를 account 도메인의 프로필 조회 포트에 위임한다. */
@Component
@RequiredArgsConstructor
public class PartyProfileAdapter implements PartyProfilePort {

    private final ClientProfileRepository clientProfileRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;

    @Override
    public Optional<Long> findClientProfileIdByAccountId(Long accountId) {
        return clientProfileRepository.findByAccountId(accountId).map(ClientProfile::getId);
    }

    @Override
    public Optional<Long> findFreelancerProfileIdByAccountId(Long accountId) {
        return freelancerProfileRepository.findByAccountId(accountId).map(FreelancerProfile::getId);
    }
}
