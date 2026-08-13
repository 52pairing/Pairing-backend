package com.pairing.chat.application.result;

import com.pairing.chat.domain.model.ChatRoom;

import java.time.LocalDateTime;

/**
 * 채팅방 조회 결과(도메인 + 표시용으로 이미 해석된 값). 프레젠테이션 팩토리는 이 값을 그대로 응답에 옮긴다.
 *
 * @param room            방 애그리거트(상태·입력활성·나가기 가능 판정에 사용)
 * @param projectTitle    프로젝트명
 * @param counterpartName 상대 표시명(뷰어 기준)
 * @param counterpartImageKey 상대 프로필 사진 오브젝트 키(등록이 선택이라 null 이 흔하다)
 * @param lastMessage     마지막 메시지 본문(없으면 null)
 * @param lastMessageAt   마지막 메시지 시각(없으면 null)
 * @param unreadCount     뷰어의 안 읽은 메시지 수
 */
public record ChatRoomView(
        ChatRoom room,
        String projectTitle,
        String counterpartName,
        String counterpartImageKey,
        String lastMessage,
        LocalDateTime lastMessageAt,
        int unreadCount
) {
}
