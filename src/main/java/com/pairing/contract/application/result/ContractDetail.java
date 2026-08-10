package com.pairing.contract.application.result;

import com.pairing.contract.domain.model.Contract;
import com.pairing.meta.domain.model.JobRole;

/**
 * 계약 상세 + 다른 도메인에서 붙인 표시값.
 *
 * <p>계약 애그리거트는 프로젝트명이나 당사자 이름을 들고 있지 않다. {@code client_profile.id} 같은
 * 참조만 갖는다. 화면에 필요한 이름은 조회 시점에 붙인다.
 *
 * <p>원본이 지워졌으면 null 이 들어온다. 계약은 5년 보관이라 프로젝트보다 오래 남는다.
 */
public record ContractDetail(
        Contract contract,
        String projectTitle,
        JobRole jobRole,
        String clientName,
        String freelancerName
) {
}
