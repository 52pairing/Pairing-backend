package com.pairing.project.application.event;

/**
 * 클라이언트가 완료 처리를 눌러 프로젝트가 완료 대기로 넘어갔다. (정책 P30)
 *
 * <p>인원별 상태({@code matching_request.status})를 완료 대기로 옮기는 쪽이 이걸 듣는다.
 * 매칭 요청은 매칭 도메인의 애그리거트라 프로젝트가 직접 바꾸지 않는다.
 *
 * <p>같은 트랜잭션에서 성공보수 정산이 함께 만들어진다. 실패하면 완료 처리도 롤백된다.
 */
public record ProjectCompletionRequestedEvent(Long projectId) {
}
