package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 로그인 계정이 이 협상의 어느 당사자인지 판정한다. 협상은 당사자를 "명함 ID"로만 알고
 * 인증은 account.id 로 들어오므로, {@link PartyProfilePort} 로 번역해 비교한다.
 * 어느 쪽도 아니면 NG_002.
 */
@Component
@RequiredArgsConstructor
public class NegotiationViewerResolver {

    private final PartyProfilePort partyProfilePort;

    /**
     * @param accountId               로그인 계정 ID
     * @param negotiationFreelancerId 협상이 가진 프리 당사자 = freelancer_profile.id
     * @param projectClientProfileId  협상 프로젝트의 client_id = client_profile.id (project 없으면 null)
     */
    public PartyRole resolve(Long accountId, Long negotiationFreelancerId, Long projectClientProfileId) {
        Optional<Long> myFreelancerProfileId = partyProfilePort.findFreelancerProfileIdByAccountId(accountId);
        if (myFreelancerProfileId.filter(id -> id.equals(negotiationFreelancerId)).isPresent()) {
            return PartyRole.FREELANCER;
        }

        if (projectClientProfileId != null) {
            Optional<Long> myClientProfileId = partyProfilePort.findClientProfileIdByAccountId(accountId);
            if (myClientProfileId.filter(id -> id.equals(projectClientProfileId)).isPresent()) {
                return PartyRole.CLIENT;
            }
        }

        throw new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT);
    }
}
