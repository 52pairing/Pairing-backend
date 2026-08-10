package com.pairing.settlement.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 상태. 결제는 버튼으로 수동 처리한다.
 *
 * <p>CANCELED 는 프로젝트가 등록 취소돼 낼 이유가 사라진 정산이다. 결제 대상에서 빠지고
 * 목록에는 이력으로 남는다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

    PENDING("결제 대기"),
    PAID("결제 완료"),
    OVERDUE("미납"),
    FAILED("결제 실패"),
    CANCELED("취소됨");

    private final String label;
}
