package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.command.CreateNegotiationCommand;
import com.pairing.negotiation.application.port.out.ChatRoomCreationPort;
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
    private final ChatRoomCreationPort chatRoomCreationPort;

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
                project.startNegotiable(),
                project.periodValue(),
                project.periodUnit());

        Negotiation negotiation = Negotiation.create(
                command.requestId(), command.projectId(), command.positionId(),
                command.freelancerId(), command.budgetCap(), conditions);

        // 불일치 조건이 0개면 협상할 게 없다 → 즉시 타결하고 사람 채팅방을 연다(AI 루프 없이 바로 채팅).
        boolean settledImmediately = conditions.isEmpty();
        if (settledImmediately) {
            negotiation.agreeWithoutConditions(command.budgetCap());
        }

        Long negotiationId = negotiationRepository.save(negotiation).getId();

        if (settledImmediately) {
            chatRoomCreationPort.createForAgreedNegotiation(negotiationId);
        }
        return negotiationId;
    }

    private void validate(CreateNegotiationCommand command) {
        if (command == null || command.snapshot() == null || command.snapshot().payUnit() == null
                || command.snapshot().payAmount() == null) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
    }
}
