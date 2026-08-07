package com.pairing.project.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 결제 상태. (요구사항 R20)
 *
 * <p>프로젝트 상태·인원별 상태와 별도로 관리한다. 착수금 수수료를 내야 모집이 시작된다.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectPaymentStatus {

    DEPOSIT_PENDING("착수금 결제 대기"),
    DEPOSIT_PAID("착수금 결제 완료"),
    SUCCESS_FEE_PENDING("성공보수 결제 대기"),
    SUCCESS_FEE_PAID("성공보수 결제 완료"),
    PAYMENT_FAILED("결제 실패");

    private final String label;
}
