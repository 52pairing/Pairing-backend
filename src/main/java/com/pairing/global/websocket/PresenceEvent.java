package com.pairing.global.websocket;

/**
 * 접속 상태 변경 push 페이로드.
 *
 * <p>구독 경로는 {@code /topic/users/{userId}/presence} 이며, 프론트엔드는 폴링 없이 이 메시지로
 * 상대방의 온라인/오프라인 표시를 갱신한다.
 *
 * @param userId 상태가 바뀐 사용자 PK
 * @param online true면 접속, false면 해제
 */
public record PresenceEvent(Long userId, boolean online) {}
