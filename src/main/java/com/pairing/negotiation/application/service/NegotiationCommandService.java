package com.pairing.negotiation.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.command.CreateNegotiationCommand;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort.ProjectView;
import com.pairing.negotiation.application.usecase.NegotiationCommandUseCase;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
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
    private final NegotiationMessageRepository messageRepository;
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
                project.startNegotiable(),
                project.periodValue(),
                project.periodUnit());

        Negotiation negotiation = Negotiation.create(
                command.requestId(), command.projectId(), command.positionId(),
                command.freelancerId(), command.budgetCap(), conditions);

        // 불일치 조건이 0개면 협상할 게 없다 → AI 루프 없이 즉시 타결한다.
        // (사람 채팅방은 타결이 아니라 계약 체결 시 열린다.)
        boolean settledImmediately = conditions.isEmpty();
        if (settledImmediately) {
            negotiation.agreeWithoutConditions(command.budgetCap());
        }

        Long negotiationId = negotiationRepository.save(negotiation).getId();

        if (settledImmediately) {
            sealAgreementSnapshot(negotiationId, negotiation);   // 최종 조건 봉인(증거 일관성)
        }
        return negotiationId;
    }

    /** 무협상 즉시 타결도 최종 조건을 해시체인에 봉인한다. 이 협상의 첫(그리고 유일한) 로그다. */
    private void sealAgreementSnapshot(Long negotiationId, Negotiation negotiation) {
        NegotiationMessage snapshot = NegotiationMessage.system(negotiationId, negotiation.getTotalRound(),
                "협상 없이 즉시 타결되었습니다. 최종 조건 봉인: " + negotiation.finalTermsSnapshot());
        snapshot.seal(messageRepository.findLatestHash(negotiationId).orElse(NegotiationMessage.GENESIS_HASH));
        messageRepository.save(snapshot);
    }

    private void validate(CreateNegotiationCommand command) {
        if (command == null || command.snapshot() == null || command.snapshot().payUnit() == null
                || command.snapshot().payAmount() == null) {
            throw new BusinessException(NegotiationErrorCode.INVALID_CONDITION);
        }
    }
}
