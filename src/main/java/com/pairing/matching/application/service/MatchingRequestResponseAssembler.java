package com.pairing.matching.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.application.result.NegotiationSummary;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link MatchingRequestResponse} 조립. 보는 쪽(클라이언트/프리랜서)에 따라 counterpartName이 달라진다. */
@Component
@RequiredArgsConstructor
class MatchingRequestResponseAssembler {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;

    MatchingRequestResponse build(MatchingRequest request, Long viewerAccountId) {
        Account viewer = accountQueryUseCase.getById(viewerAccountId);
        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(request.getProjectId(),
                request.getPositionId());
        FreelancerCardSummary freelancer = freelancerDirectoryPort.findCardSummary(request.getFreelancerId());
        String counterpartName = viewer.getRole() == Role.CLIENT ? freelancer.name() : position.companyName();

        NegotiationSummary negotiation = negotiationPort.findSummaryByRequestId(request.getId()).orElse(null);

        return new MatchingRequestResponse(
                request.getId(),
                request.getProjectId(),
                position.projectTitle(),
                request.getPositionId(),
                position.jobRole(),
                counterpartName,
                position.companyName(),
                position.companyProfile(),
                position.requiredSkills(),
                position.minCareerYears(),
                position.workLabel(),
                position.periodLabel(),
                position.startDesiredDate(),
                request.getStatus(),
                position.budgetAmount(),
                request.getRequestedAt(),
                request.getExpiresAt(),
                request.getRespondedAt(),
                negotiation != null ? negotiation.currentRound() : null,
                negotiation != null ? negotiation.maxRound() : null,
                negotiation != null ? negotiation.newProposalCount() : null,
                negotiation != null ? negotiation.negotiationId() : null
        );
    }
}
