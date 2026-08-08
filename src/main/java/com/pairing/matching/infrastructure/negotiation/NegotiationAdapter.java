package com.pairing.matching.infrastructure.negotiation;

import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.result.NegotiationSummary;
import com.pairing.negotiation.application.usecase.NegotiationCommandUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase.NegotiationProgress;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link NegotiationPort}의 실제 구현. negotiation 도메인의 인바운드 포트 2개를 그대로 위임한다. */
@Component
@RequiredArgsConstructor
public class NegotiationAdapter implements NegotiationPort {

    private final NegotiationCommandUseCase negotiationCommandUseCase;
    private final NegotiationProgressUseCase negotiationProgressUseCase;

    @Override
    public Long createNegotiation(CreateNegotiationCommand command) {
        FreelancerConditionSnapshot snapshot = new FreelancerConditionSnapshot(
                command.payUnit(),
                command.payAmount(),
                command.workStyle(),
                command.workForm(),
                command.availableFrom(),
                command.startNegotiable(),
                command.minAcceptAmount(),
                command.periodValue(),
                command.periodUnit());

        com.pairing.negotiation.application.command.CreateNegotiationCommand negotiationCommand =
                new com.pairing.negotiation.application.command.CreateNegotiationCommand(
                        command.requestId(),
                        command.projectId(),
                        command.positionId(),
                        command.freelancerId(),
                        command.budgetCap(),
                        snapshot);

        return negotiationCommandUseCase.create(negotiationCommand);
    }

    @Override
    public Optional<NegotiationSummary> findSummaryByRequestId(Long requestId) {
        return negotiationProgressUseCase.findProgressByRequestId(requestId)
                .map(NegotiationAdapter::toSummary);
    }

    private static NegotiationSummary toSummary(NegotiationProgress progress) {
        return new NegotiationSummary(progress.negotiationId(), progress.currentRound(), progress.maxRound(),
                progress.newProposalCount());
    }
}
