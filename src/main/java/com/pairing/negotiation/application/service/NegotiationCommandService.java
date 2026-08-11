package com.pairing.negotiation.application.service;

import com.pairing.contract.application.usecase.ContractCreationUseCase;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.usecase.MatchingNegotiationOutcomeUseCase;
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
    // 무협상 즉시 타결도 계약서를 만들어야 한다(방향: negotiation → contract).
    private final ContractCreationUseCase contractCreationUseCase;
    // 즉시 타결 시 매칭 요청도 CONTRACT_PENDING 으로 올린다(방향: negotiation → matching).
    // 라운드를 도는 경로는 NegotiationLoopService 가 같은 포트를 이미 쓰고 있다.
    private final MatchingNegotiationOutcomeUseCase matchingNegotiationOutcomeUseCase;

    @Override
    public Long create(CreateNegotiationCommand command) {
        validate(command);

        // 클라 희망값(project)은 협상이 직접 조회한다. 없으면 잘못된 요청 → 예외로 수락까지 롤백.
        ProjectView project = projectReaderPort.findById(command.projectId())
                .orElseThrow(() -> new BusinessException(NegotiationErrorCode.INVALID_CONDITION));

        List<NegotiationCondition> conditions = NegotiationConditionCalculator.compute(
                command.budgetCap(),
                command.snapshot(),
                project.workStyle(),
                project.workForm(),
                project.startDesiredDate(),
                project.startNegotiable(),
                project.periodValue(),
                project.periodUnit());

        long freelancerMonthlyPay = command.snapshot().monthlyPay();
        Negotiation negotiation = Negotiation.create(
                command.requestId(), command.projectId(), command.positionId(),
                command.freelancerId(), command.budgetCap(), freelancerMonthlyPay, conditions);

        // 불일치 조건이 0개면 협상할 게 없다 → AI 루프 없이 즉시 타결한다.
        // (사람 채팅방은 타결이 아니라 계약 체결 시 열린다.)
        //
        // 합의 금액은 예산 상한(budgetCap)이 아니라 프리랜서가 제시한 월 단가다. 조건이 0개라는 건
        // 그 단가가 상한 이내라 다툴 게 없다는 뜻이므로, 상한을 합의값으로 쓰면 프리가 요구한 적 없는
        // 금액이 계약서에 찍히고 클라도 상한을 전액 지불하게 된다(양쪽 모두에게 불리).
        boolean settledImmediately = conditions.isEmpty();
        if (settledImmediately) {
            negotiation.agreeWithoutConditions(freelancerMonthlyPay);
        }

        Long negotiationId = negotiationRepository.save(negotiation).getId();

        if (settledImmediately) {
            sealAgreementSnapshot(negotiationId, negotiation);   // 최종 조건 봉인(증거 일관성)
            // 매칭 요청도 CONTRACT_PENDING 으로 올린다. 이게 없으면 즉시 타결 건만 NEGOTIATING 에
            // 갇힌다 — 라운드를 도는 경로는 NegotiationLoopService.answer() 가 이미 부르고 있어서
            // 여기만 빠져 있었다(2026-08-10, 3번이 계약 도메인 붙이며 발견).
            matchingNegotiationOutcomeUseCase.markNegotiationAgreed(negotiation.getRequestId());
            // 타결 경로가 둘이라 여기도 계약서를 만든다. 저장 뒤여야 한다 — 계약 쪽이
            // getAgreedForContract 로 협상을 다시 읽으므로 save 전에 부르면 못 찾는다.
            contractCreationUseCase.createFromNegotiation(negotiationId);
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
