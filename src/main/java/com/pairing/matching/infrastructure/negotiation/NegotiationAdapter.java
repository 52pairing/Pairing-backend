package com.pairing.matching.infrastructure.negotiation;

import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.result.NegotiationSummary;
import com.pairing.negotiation.application.usecase.NegotiationCommandUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase;
import com.pairing.negotiation.application.usecase.NegotiationProgressUseCase.NegotiationProgress;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link NegotiationPort}의 실제 구현. negotiation 도메인의 인바운드 포트를 그대로 위임한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationAdapter implements NegotiationPort {

    private final NegotiationCommandUseCase negotiationCommandUseCase;
    private final NegotiationProgressUseCase negotiationProgressUseCase;
    private final NegotiationQueryUseCase negotiationQueryUseCase;

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

    /**
     * requestId → negotiationId → 타결 스냅샷 순으로 두 번 위임한다. 협상 도메인에 requestId로 바로
     * 타결값을 주는 통로가 없어서다.
     *
     * <p>{@code getAgreedForContract}는 <b>타결 전이면 NOT_AGREED 예외를 던진다.</b> 매칭 요청 상태로
     * 걸러 부르는 게 원칙이지만(호출부 책임), 상태와 협상이 어긋난 데이터가 있어도 가드가 통째로
     * 실패하면 안 되므로 여기서도 잡아서 empty로 바꾼다 — 그때는 호출부가 희망 단가로 넘어간다.
     */
    @Override
    public Optional<Long> findAgreedMonthlyPay(Long requestId) {
        return negotiationProgressUseCase.findProgressByRequestId(requestId)
                .flatMap(progress -> {
                    try {
                        return Optional.ofNullable(
                                negotiationQueryUseCase.getAgreedForContract(progress.negotiationId())
                                        .agreedAmount());
                    } catch (BusinessException e) {
                        log.debug("타결가 조회 불가 (requestId={}, 사유={})", requestId, e.getMessage());
                        return Optional.empty();
                    }
                });
    }

    private static NegotiationSummary toSummary(NegotiationProgress progress) {
        return new NegotiationSummary(progress.negotiationId(), progress.currentRound(), progress.maxRound(),
                progress.newProposalCount());
    }
}
