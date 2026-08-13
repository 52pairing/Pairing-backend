package com.pairing.contract.application.service;

import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.application.port.ContractFileReaderPort;
import com.pairing.contract.application.port.FreelancerGradeReaderPort;
import com.pairing.contract.application.usecase.ContractCommandUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractSignature;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.command.CreateFreelancerDepositCommand;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ContractCommandService implements ContractCommandUseCase {

    /** 자체 구현이라 외부 인증 수단이 없다. 로그인 세션으로 본인 확인한 뒤 동의를 받는다. */
    private static final String VERIFICATION_METHOD = "SESSION";

    private static final String LINK_PREFIX = "/contracts/";
    private static final String WAITING_TITLE = "상대방이 계약서에 서명했습니다";
    private static final String WAITING_CONTENT = "계약서를 확인하고 서명해 주세요. 양측이 서명하면 계약이 체결됩니다.";
    private static final String CONCLUDED_TITLE = "계약이 체결되었습니다";

    /**
     * 체결 알림은 받는 사람에 따라 다음 할 일이 다르다.
     *
     * <p>프리랜서는 이 시점에 착수금 수수료가 청구되는데(P27), 서명 직후 화면을 떠나면 청구된 것을
     * 모르고 지나친다. 수수료 알림을 따로 보내면 체결 알림과 시점이 겹쳐 두 개가 연달아 가므로
     * 여기에 합친다.
     *
     * <p>링크는 양쪽 다 계약서로 둔다. 결제 화면으로 바로 보내면 계약서를 확인하지 않은 채
     * 결제하게 된다.
     */
    private static final String CONCLUDED_CONTENT_CLIENT =
            "양측 서명이 완료되어 계약이 체결되었습니다. 채팅으로 프로젝트를 시작할 수 있습니다.";
    private static final String CONCLUDED_CONTENT_FREELANCER =
            "양측 서명이 완료되어 계약이 체결되었습니다. 착수금 수수료를 결제해 주세요.";

    private final ContractRepository contractRepository;
    private final ContractFileReaderPort contractFileReaderPort;
    private final ProjectCommandUseCase projectCommandUseCase;
    private final DepositSettlementUseCase depositSettlementUseCase;
    private final FreelancerGradeReaderPort freelancerGradeReaderPort;
    private final NotificationCreateUseCase notificationCreateUseCase;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean sign(SignContractCommand command) {
        Contract contract = getForParty(command.contractId(), command.accountId());

        // 타임스탬프 토큰은 외부 시각 보증 기관을 붙일 때 채운다. 지금은 서명 시각만 남긴다.
        boolean concluded = contract.sign(command.accountId(), VERIFICATION_METHOD,
                command.ipAddress(), command.userAgent(), null,
                requireSignatureFile(command.signatureFileId()));

        contractRepository.updateState(contract);

        if (concluded) {
            conclude(contract);
        }
        notifySigned(contract, command.accountId(), concluded);
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

        // 인원별 상태를 계약 완료로 옮기는 쪽(매칭)과, 1:1 채팅방을 여는 쪽이 듣는다.
        // 채팅은 커밋 뒤에 연다. 이유는 ContractChatListener 주석 참고.
        eventPublisher.publishEvent(new ContractSignedEvent(contract.getId(), contract.getProjectId(),
                contract.getPositionId(), contract.getFreelancerId()));
    }

    /**
     * 서명 알림.
     *
     * <p>아직 한쪽만 서명했으면 <b>상대에게만</b> 보낸다. 남은 사람이 서명해야 계약이 성립하므로
     * 그 사람을 부르는 것이 목적이다. 체결됐으면 양쪽에 결과를 알린다.
     *
     * <p>실패해도 삼킨다. 알림 때문에 서명이 롤백되면 사용자는 버튼을 눌러도 아무 일이 안 일어나는
     * 것처럼 보인다. 알림 없는 서명이 서명 안 되는 것보다 낫다.
     */
    private void notifySigned(Contract contract, Long signerAccountId, boolean concluded) {
        try {
            if (concluded) {
                contract.getSignatures().forEach(signature ->
                        notify(contract, signature.getAccountId(), CONCLUDED_TITLE,
                                concludedContent(signature.getPartyRole())));
                return;
            }

            contract.getSignatures().stream()
                    .map(ContractSignature::getAccountId)
                    .filter(accountId -> !accountId.equals(signerAccountId))
                    .forEach(accountId -> notify(contract, accountId, WAITING_TITLE, WAITING_CONTENT));

        } catch (Exception e) {
            log.warn("계약 서명 알림 실패. 서명 자체는 처리됐다. contractId={}", contract.getId(), e);
        }
    }

    /** 착수금 수수료를 내는 쪽은 프리랜서뿐이다(P27). 클라이언트 착수금은 프로젝트 등록 때 끝난다. */
    private String concludedContent(PartyRole partyRole) {
        return partyRole == PartyRole.FREELANCER
                ? CONCLUDED_CONTENT_FREELANCER
                : CONCLUDED_CONTENT_CLIENT;
    }

    private void notify(Contract contract, Long accountId, String title, String content) {
        notificationCreateUseCase.create(new CreateNotificationCommand(
                accountId, NotificationType.CONTRACT_SIGNED, title, content,
                LINK_PREFIX + contract.getId()));
    }

    /**
     * 서명 이미지 확인. 선택이라 없으면 그대로 넘긴다.
     *
     * <p>존재하지 않는 fileId 를 그대로 저장하면 나중에 PDF 를 그릴 때 서명란이 깨진다. 그 시점에는
     * 이미 체결된 계약이라 되돌릴 수 없으므로, 서명 시점에 막는다.
     */
    private Long requireSignatureFile(Long signatureFileId) {
        if (signatureFileId == null) {
            return null;
        }
        if (!contractFileReaderPort.exists(signatureFileId)) {
            throw new BusinessException(ContractErrorCode.SIGNATURE_NOT_FOUND);
        }
        return signatureFileId;
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
