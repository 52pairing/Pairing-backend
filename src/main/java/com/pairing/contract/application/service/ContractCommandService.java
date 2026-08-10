package com.pairing.contract.application.service;

import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.usecase.ContractCommandUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 서명·거부.
 *
 * <p>자체 전자서명이라 외부 서비스를 부르지 않는다. 동의 클릭을 서명으로 보고 시각·접속 정보를
 * 증거로 남긴다.
 *
 * <p>체결(양측 서명 완료)은 같은 트랜잭션에서 프로젝트 인원 확정까지 이어진다. 인원이 다 차면
 * 프로젝트가 진행중으로 넘어가는데, 서명만 되고 인원이 안 잡히면 상태가 어긋나기 때문이다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ContractCommandService implements ContractCommandUseCase {

    /** 자체 구현이라 외부 인증 수단이 없다. 로그인 세션으로 본인 확인한 뒤 동의를 받는다. */
    private static final String VERIFICATION_METHOD = "SESSION";

    private final ContractRepository contractRepository;
    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public boolean sign(SignContractCommand command) {
        Contract contract = getForParty(command.contractId(), command.accountId());

        // 타임스탬프 토큰은 외부 시각 보증 기관을 붙일 때 채운다. 지금은 서명 시각만 남긴다.
        boolean concluded = contract.sign(command.accountId(), VERIFICATION_METHOD,
                command.ipAddress(), command.userAgent(), null, null);

        contractRepository.updateState(contract);

        if (concluded) {
            // 필요 인원이 다 차면 프로젝트가 진행중으로 넘어간다. 실패하면 서명도 함께 롤백한다.
            projectCommandUseCase.confirmPosition(contract.getPositionId());
        }
        return concluded;
    }

    @Override
    public void reject(Long contractId, Long accountId, String reason) {
        Contract contract = getForParty(contractId, accountId);

        contract.reject(accountId, reason);
        contractRepository.updateState(contract);
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
