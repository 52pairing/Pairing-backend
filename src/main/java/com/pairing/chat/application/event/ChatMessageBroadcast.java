package com.pairing.chat.application.event;

import com.pairing.chat.domain.model.ChatMessageType;
import com.pairing.global.infrastructure.s3.CdnMappable;

import java.time.LocalDateTime;

/**
 * 새 채팅 메시지 실시간 알림. 구독 경로 {@code /topic/chat-rooms/{chatRoomId}} 로 나간다.
 *
 * <p>"내가 보낸 메시지인지(mine)"는 수신자마다 다르므로 담지 않는다. 프론트가 {@code senderId} 를
 * 자기 계정과 비교해 판단한다.
 *
 * <p>{@link CdnMappable} 을 구현하는 이유는 {@code senderImageUrl} 이 DB 에는 오브젝트 키로 있어서다.
 * REST 응답만 절대 URL 로 바꾸면 <b>같은 화면에서 REST 는 URL, 실시간은 키</b>가 되어 아바타가
 * 실시간 메시지에서만 깨진다. STOMP 메시지 컨버터도 부트가 만든 ObjectMapper 를 쓰므로 같은 변환이 걸린다.
 */
public record ChatMessageBroadcast(
        Long chatRoomId,
        Long messageId,
        Long senderId,
        String senderName,
        String senderImageUrl,
        ChatMessageType messageType,
        String content,
        LocalDateTime createdAt
) implements CdnMappable {
}
