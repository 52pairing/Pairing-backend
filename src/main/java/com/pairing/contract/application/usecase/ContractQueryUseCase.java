package com.pairing.contract.application.usecase;

import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.domain.model.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 계약 조회 인바운드 포트.
 *
 * <p>계약 당사자 두 명만 볼 수 있다. 클라이언트·프리랜서 모두 같은 API 를 쓴다.
 */
public interface ContractQueryUseCase {

    /** 내 계약 목록. status 가 null 이면 전체 상태를 조회한다. */
    Page<ContractSummary> findMine(Long accountId, ContractStatus status, Pageable pageable);

    /** 상세 조회. 없으면 CT_001, 당사자가 아니면 CT_002. */
    ContractDetail getDetail(Long contractId, Long accountId);
}
