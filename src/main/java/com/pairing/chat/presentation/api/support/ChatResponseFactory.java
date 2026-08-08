package com.pairing.chat.presentation.api.support;

import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.result.ChatRoomView;
import com.pairing.chat.domain.model.ChatMessage;
import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.presentation.api.response.ChatMessageResponse;
import com.pairing.chat.presentation.api.response.ChatRoomResponse;

/** 채팅 조회 결과(View) → 응답 DTO 변환. 뷰어 관점(mine·leaveEnabled)은 View 에서 이미 결정된다. */
public final class ChatResponseFactory {

    private ChatResponseFactory() {
    }

    public static ChatRoomResponse room(ChatRoomView view) {
        ChatRoom room = view.room();
        return new ChatRoomResponse(
                room.getId(),
                room.getNegotiationId(),
                view.projectTitle(),
                view.counterpartName(),
                room.getStatus(),
                room.isInputEnabled(),
                room.isLeaveAllowed(),
                view.lastMessage(),
                view.lastMessageAt(),
                view.unreadCount());
    }

    public static ChatMessageResponse message(ChatMessageView view) {
        ChatMessage message = view.message();
        return new ChatMessageResponse(
                message.getId(),
                message.getSenderId(),
                view.senderName(),
                view.mine(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt());
    }
}
