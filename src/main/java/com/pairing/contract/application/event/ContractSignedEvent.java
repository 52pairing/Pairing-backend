package com.pairing.contract.application.event;

/**
 * 계약이 체결됐다. 양측 서명이 모두 끝난 시점이다.
 *
 * <p>인원별 상태({@code matching_request.status})를 계약 완료로 옮기는 쪽이 이걸 듣는다.
 * 매칭 요청은 매칭 도메인의 애그리거트라 계약이 직접 바꾸지 않는다.
 *
 * <p>매칭은 {@code findByPositionIdAndFreelancerId} 로 요청 건을 찾을 수 있어
 * {@code requestId} 를 따로 넘기지 않는다. 계약이 그 값을 들고 있지 않기도 하다.
 */
public record ContractSignedEvent(Long contractId, Long projectId, Long positionId, Long freelancerId) {
}
