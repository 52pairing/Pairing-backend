package com.pairing.settlement.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수수료 단계. (요구사항 R34)
 *
 * <p>착수금은 계약 체결 시, 성공보수는 프로젝트 완료 시 발생한다.
 * 실제 용역비는 플랫폼을 거치지 않고 클라이언트가 프리랜서에게 직접 지급한다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementPhase {

    DEPOSIT("착수금 수수료"),
    SUCCESS_FEE("성공보수 수수료");

    private final String label;
}
