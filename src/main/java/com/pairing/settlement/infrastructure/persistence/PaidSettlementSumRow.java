package com.pairing.settlement.infrastructure.persistence;

import com.pairing.settlement.domain.model.SettlementPhase;

/**
 * 결제 완료된 수수료의 단계별 집계 한 줄.
 *
 * <p>단계마다 따로 세면 쿼리가 단계 수만큼 나간다. {@code phase} 로 묶어 한 번에 받고,
 * 합계와 화면 표기는 애플리케이션이 만든다.
 *
 * <p>{@code projectCount} 는 {@code COUNT(DISTINCT project_id)} 다. 한 프로젝트에 여러 계약이
 * 걸리면 그 사람의 수수료도 여러 건이라, 단순 행 수로 세면 "완료 프로젝트 수"가 부풀어 오른다.
 */
public record PaidSettlementSumRow(SettlementPhase phase, Long feeSum, Long projectCount) {
}
