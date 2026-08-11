package com.pairing.project.application.event;

/**
 * 성공보수 수수료 결제가 끝나 프로젝트가 종료됐다. (정책 P30)
 *
 * <p>인원별 상태({@code matching_request.status})를 종료로 옮기는 쪽이 이걸 듣는다.
 * 프로젝트 상태 흐름의 마지막 단계이며, 이 시점부터 리뷰 작성이 열린다(P51).
 */
public record ProjectClosedEvent(Long projectId) {
}
