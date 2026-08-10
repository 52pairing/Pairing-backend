package com.pairing.contract.application.result;

import com.pairing.contract.domain.model.Contract;

/**
 * 계약 목록 항목.
 *
 * <p>{@code counterpartName} 은 보는 사람에 따라 달라진다. 클라이언트가 보면 프리랜서명,
 * 프리랜서가 보면 기업명이다. 조회 시점에 뷰어 계정으로 판단해 채운다.
 *
 * <p>{@code signatureRequired} 는 "내가 아직 서명하지 않았는가" 다. 목록에서 서명 대기 배지를
 * 띄우는 데 쓴다.
 */
public record ContractSummary(
        Contract contract,
        String projectTitle,
        String counterpartName,
        boolean signatureRequired
) {
}
