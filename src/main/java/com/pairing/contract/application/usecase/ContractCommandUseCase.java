package com.pairing.contract.application.usecase;

import com.pairing.contract.application.command.SignContractCommand;

/** 계약 상태를 바꾸는 인바운드 포트. */
public interface ContractCommandUseCase {

    /**
     * 서명. 양측이 모두 서명하면 체결(SIGNED)되고 해당 포지션 인원이 확정된다. (요구사항 R43)
     *
     * <p>계약 당사자가 아니면 CT_002, 서명 대기 상태가 아니면 CT_003,
     * 이미 서명·거부했으면 CT_004.
     *
     * @return 이 서명으로 계약이 체결됐으면 true. 아직 상대가 남았으면 false
     */
    boolean sign(SignContractCommand command);

    /**
     * 서명 거부. 한쪽이 거부하면 계약 전체가 종료된다.
     *
     * <p>에러 코드는 {@link #sign} 과 같다. 거부한 계약은 되살릴 수 없고 새로 만들어야 한다.
     */
    void reject(Long contractId, Long accountId, String reason);
}
