package com.pairing.contract.application.service;

import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.application.port.FreelancerGradeReaderPort;
import com.pairing.contract.application.usecase.ContractCommandUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.command.CreateFreelancerDepositCommand;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 서명·거부.
 *
 * <p>자체 전자서명이라 외부 서비스를 부르지 않는다. 동의 클릭을 서명으로 보고 시각·접속 정보를
 * 증거로 남긴다.
 *
 * <p>체결(양측 서명 완료)은 같은 트랜잭션에서 프로젝트 인원 확정과 채팅방 개설까지 이어진다.
 * 인원이 다 차면 프로젝트가 진행중으로 넘어가는데, 서명만 되고 인원이 안 잡히면 상태가 어긋난다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ContractCommandService implements ContractCommandUseCase {

    /** 자체 구현이라 외부 인증 수단이 없다. 로그인 세션으로 본인 확인한 뒤 동의를 받는다. */
    private static final String VERIFICATION_METHOD = "SESSION";

    private final ContractRepository contractRepository;
    private final ProjectCommandUseCase projectCommandUseCase;
    private final ChatActivationUseCase chatActivationUseCase;
    private final DepositSettlementUseCase depositSettlementUseCase;
    private final FreelancerGradeReaderPort freelancerGradeReaderPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean sign(SignContractCommand command) {
        Contract contract = getForParty(command.contractId(), command.accountId());

        // 타임스탬프 토큰은 외부 시각 보증 기관을 붙일 때 채운다. 지금은 서명 시각만 남긴다.
        boolean concluded = contract.sign(command.accountId(), VERIFICATION_METHOD,
                command.ipAddress(), command.userAgent(), null, null);

        contractRepository.updateState(contract);

        if (concluded) {
            conclude(contract);
        }
        return concluded;
    }

    @Override
    public void reject(Long contractId, Long accountId, String reason) {
        Contract contract = getForParty(contractId, accountId);

        contract.reject(accountId, reason);
        contractRepository.updateState(contract);
    }

    /**
     * 체결 처리. 전부 같은 트랜잭션이라 하나라도 실패하면 서명까지 되돌아간다.
     *
     * <p>여기서 프로젝트를 진행중으로 넘기지는 않는다. 프리랜서 착수금 수수료까지 결제돼야
     * 하는데(P27) 그 시점은 정산 도메인이 안다. 여기서는 인원만 확정한다.
     */
    private void conclude(Contract contract) {
        // 포지션 인원 확정. 다 차면 그 포지션이 닫힌다.
        projectCommandUseCase.confirmPosition(contract.getPositionId());

        // 프리랜서 착수금 수수료는 이 시점에 발생한다(P27). 계약 총액이 기준이다.
        depositSettlementUseCase.createFreelancerDeposit(new CreateFreelancerDepositCommand(
                contract.getProjectId(),
                contract.getId(),
                contract.accountIdOf(PartyRole.FREELANCER),
                contract.getTotalAmount(),
                freelancerGradeReaderPort.findGrade(contract.getFreelancerId())));

        // 프로젝트 진행 대화는 계약이 성립한 뒤에 시작한다. 협상 타결만으로는 방이 열리지 않는다.
        // 멱등이라 재시도해도 방이 두 개 생기지 않는다.
        chatActivationUseCase.openForSignedContract(contract.getNegotiationId());

        // 인원별 상태를 계약 완료로 옮기는 쪽(매칭)이 듣는다.
        eventPublisher.publishEvent(new ContractSignedEvent(contract.getId(), contract.getProjectId(),
                contract.getPositionId(), contract.getFreelancerId()));
    }

    /** 계약 존재 + 당사자 확인. 갑·을 두 명만 서명·거부할 수 있다. */
    private Contract getForParty(Long contractId, Long accountId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        if (!contract.isPartyOf(accountId)) {
            throw new BusinessException(ContractErrorCode.NOT_CONTRACT_PARTY);
        }
        return contract;
    }
}
