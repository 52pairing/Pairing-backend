package com.pairing.global.config;

import com.pairing.contract.application.port.ContractPartyReaderPort.ClientParty;
import com.pairing.contract.application.port.ContractPartyReaderPort.FreelancerParty;
import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 리뷰를 만들려면 계약이 있어야 한다. 계약을 진짜로 만들려면 매칭→협상→타결까지 태워야 해서,
 * 리뷰·등급·홈 테스트에서는 계약 조회만 이 스텁으로 대신한다.
 *
 * <p>계약 자체의 동작은 계약 도메인 테스트가 본다. 여기서는 "그 계약의 당사자와 프로젝트가 무엇인지"만
 * 필요하다.
 */
public final class ContractDetailStub {

    private ContractDetailStub() {
        throw new IllegalStateException("Utility class");
    }

    public static ContractDetail of(Long contractId, Long projectId, String projectTitle,
                                    Long clientAccountId, String companyName,
                                    Long freelancerAccountId, String freelancerName) {
        Contract contract = mock(Contract.class);
        given(contract.getId()).willReturn(contractId);
        given(contract.getProjectId()).willReturn(projectId);
        // 리뷰 작성 자격 판정이 계약 상태와 프로젝트 상태를 함께 본다. 테스트 프로젝트는 CLOSED 로 심는다.
        given(contract.getStatus()).willReturn(ContractStatus.COMPLETED);

        return new ContractDetail(
                contract,
                projectTitle,
                null,
                new ClientParty(clientAccountId, companyName, null, null, null, null),
                new FreelancerParty(freelancerAccountId, freelancerName, null, null, null, null),
                List.of(),
                Map.of()
        );
    }
}
