package com.pairing.contract.application.result;

import com.pairing.contract.application.port.ContractPartyReaderPort.ClientParty;
import com.pairing.contract.application.port.ContractPartyReaderPort.FreelancerParty;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractClause;
import com.pairing.meta.domain.model.JobRole;

import java.util.List;
import java.util.Map;

/**
 * 계약 상세 + 다른 도메인에서 붙인 표시값.
 *
 * <p>계약 애그리거트는 프로젝트명이나 당사자 이름을 들고 있지 않다. {@code client_profile.id} 같은
 * 참조만 갖는다. 화면에 필요한 이름은 조회 시점에 붙인다.
 *
 * <p>원본이 지워졌으면 null 이 들어온다. 계약은 5년 보관이라 프로젝트보다 오래 남는다.
 *
 * <p>{@code clauses} 는 계약서 본문이다. 조회할 때마다 다시 만든다 — 문구가 전부 고정이고
 * 값은 계약이 들고 있어 결과가 항상 같다.
 *
 * <p>{@code signatureImageUrls} 는 서명 그림의 주소를 계정 ID 로 찾는 표다. 서명은 계약이
 * {@code file_id} 만 들고 있어 주소는 조회 시점에 붙인다. 안 그린 서명은 키가 없다.
 */
public record ContractDetail(
        Contract contract,
        String projectTitle,
        JobRole jobRole,
        ClientParty client,
        FreelancerParty freelancer,
        List<ContractClause> clauses,
        Map<Long, String> signatureImageUrls
) {

    /** 서명 현황에 붙일 이름. 프로필이 지워졌으면 null 이다. */
    public String clientName() {
        return client.companyName();
    }

    public String freelancerName() {
        return freelancer.name();
    }
}
