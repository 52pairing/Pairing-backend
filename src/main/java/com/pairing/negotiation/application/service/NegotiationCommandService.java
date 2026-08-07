package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.command.CreateNegotiationCommand;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.usecase.NegotiationCommandUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.domain.service.NegotiationConditionCalculator;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class NegotiationCommandService implements NegotiationCommandUseCase {

    private final NegotiationRepository negotiationRepository;
    private final ProjectReaderPort projectReaderPort;

    @Override
    public Long create(CreateNegotiationCommand command) {
        validate(command);

        // 클라 희망값(project)은 협상이 직접 조회한다. 없으면 잘못된 요청 → 예외로 수락까지 롤백.
        ProjectView project = projectReaderPort.findById(command.projectId())
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.INVALID_CONDITION));

        List<NegotiationCondition> conditions = NegotiationConditionCalculator.compute(
                command.budgetCap(),
                command.snapshot(),
                project.budgetAmount(),
                project.workStyle(),
                project.workForm(),
                project.startDesiredDate(),
                project.startNegotiable());

        Negotiation negotiation = Negotiation.create(
                command.requestId(), command.projectId(), command.positionId(),
                command.freelancerId(), command.budgetCap(), conditions);

        return negotiationRepository.save(negotiation).getId();
    }

    private void validate(CreateNegotiationCommand command) {
        if (command == null || command.snapshot() == null || command.snapshot().payUnit() == null
                || command.snapshot().payAmount() == null) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
    }
}
