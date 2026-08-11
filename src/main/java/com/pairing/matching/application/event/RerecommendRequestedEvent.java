package com.pairing.matching.application.event;

/**
 * 재추천 회차가 열렸다는 신호. 실제 후보 채우기(AI 호출)는 이 이벤트를 받아 비동기로 처리한다.
 *
 * <p>매칭 도메인 안에서만 쓰는 내부 이벤트다. 재추천 API는 검증과 회차 생성까지만 동기로 하고
 * 바로 202로 응답하는데, 그 다음 단계(AI 호출)를 같은 트랜잭션에서 이어 하면 응답이 그만큼
 * 늦어진다. 커밋 후에 시작해야 비동기 스레드가 방금 만든 회차를 조회할 수 있어 이벤트를 쓴다.
 *
 * @param roundId 방금 생성된 회차 ID
 * @param clientAccountId 완료 알림을 받을 클라이언트 계정 ID
 */
public record RerecommendRequestedEvent(Long roundId, Long clientAccountId) {
}
