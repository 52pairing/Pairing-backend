package com.pairing.contract.application.result;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.contract.domain.model.Contract;
import com.pairing.meta.domain.model.JobRole;

/**
 * 계약 목록 항목.
 *
 * <p>{@code counterpartName} 은 보는 사람에 따라 달라진다. 클라이언트가 보면 프리랜서명,
 * 프리랜서가 보면 기업명이다. 조회 시점에 뷰어 계정으로 판단해 채운다.
 *
 * <p>{@code signatureRequired} 는 "내가 아직 서명하지 않았는가" 다. 목록에서 서명 대기 배지를
 * 띄우는 데 쓴다. {@code clientSigned}/{@code freelancerSigned} 는 이와 별개로 양측 진행 상황을
 * 함께 보여주기 위한 값이다. 뷰어가 누구든 같은 값이다.
 *
 * <p>{@code depositPaid} 와 {@code payableSettlementId} 는 계약이 아니라 <b>정산</b> 값이다.
 * 체결까지 끝난 계약이라도 프리랜서 착수금을 내지 않으면 프로젝트가 진행중으로 넘어가지 않아(P47)
 * 화면이 그 차이를 보여줘야 하고, 결제 버튼은 어느 정산을 결제할지 알아야 한다.
 *
 * <p>{@code clientBusinessField} 는 갑의 업종이다. 프리랜서 화면 카드가 기업명 옆에 찍는다.
 * 클라이언트가 보는 화면에서는 상대가 프리랜서라 쓰지 않는다.
 */
public record ContractSummary(
        Contract contract,
        String projectTitle,
        JobRole jobRole,
        String counterpartName,
        BusinessField clientBusinessField,
        boolean signatureRequired,
        boolean clientSigned,
        boolean freelancerSigned,
        boolean depositPaid,
        Long payableSettlementId
) {
}
