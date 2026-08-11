package com.pairing.settlement.application.event;

/**
 * 프로젝트가 진행중으로 넘어갔다. 전원 계약 완료 + 프리랜서 착수금 결제 완료 시점이다. (P27)
 *
 * <p>인원별 상태({@code matching_request.status})를 진행중으로 옮기는 쪽이 이걸 듣는다.
 * 매칭 요청은 매칭 도메인의 애그리거트라 정산이 직접 바꾸지 않는다.
 *
 * <p>프로젝트가 실제로 전환됐을 때만 발행한다. 인원이 덜 찼거나 이미 진행중이면 나오지 않는다.
 */
public record ProjectProgressStartedEvent(Long projectId) {
}
