package com.pairing.chat.application.result;

import com.pairing.chat.domain.model.ChatMessage;

/**
 * 채팅 메시지 조회 결과(도메인 + 뷰어 관점).
 *
 * @param message    메시지
 * @param senderName 보낸 사람 표시명(시스템 메시지는 null)
 * @param mine       뷰어 본인이 보낸 메시지인지
 */
public record ChatMessageView(
        ChatMessage message,
        String senderName,
        boolean mine
) {
}
