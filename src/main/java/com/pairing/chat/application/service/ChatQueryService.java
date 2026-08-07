package com.pairing.chat.application.service;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.chat.application.port.out.ChatDirectoryPort.NegotiationParties;
import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.result.ChatRoomView;
import com.pairing.chat.application.usecase.ChatQueryUseCase;
import com.pairing.chat.domain.model.ChatMessage;
import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.model.ChatRoomMember;
import com.pairing.chat.domain.repository.ChatMessageRepository;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatQueryService implements ChatQueryUseCase {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatDirectoryPort chatDirectoryPort;

    @Override
    public List<ChatRoomView> findMyRooms(Long accountId) {
        return chatRoomRepository.findActiveRoomsByAccountId(accountId).stream()
                .map(room -> toRoomView(room, accountId))
                .toList();
    }

    @Override
    public ChatRoomView getRoom(Long chatRoomId, Long accountId) {
        ChatRoom room = load(chatRoomId);
        room.requireParticipant(accountId);
        return toRoomView(room, accountId);
    }

    @Override
    public ChatRoomView getRoomByNegotiation(Long negotiationId, Long accountId) {
        ChatRoom room = chatRoomRepository.findByNegotiationId(negotiationId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
        room.requireParticipant(accountId);
        return toRoomView(room, accountId);
    }

    @Override
    public Page<ChatMessageView> findMessages(Long chatRoomId, Long accountId, Pageable pageable) {
        ChatRoom room = load(chatRoomId);
        room.requireParticipant(accountId);

        Map<Long, String> nameCache = new HashMap<>();
        return chatMessageRepository.findByChatRoomId(chatRoomId, pageable)
                .map(message -> new ChatMessageView(message, senderName(message, nameCache),
                        message.isSentBy(accountId)));
    }

    @Override
    public int countTotalUnread(Long accountId) {
        return chatRoomRepository.findActiveRoomsByAccountId(accountId).stream()
                .mapToInt(room -> unreadFor(room, accountId))
                .sum();
    }

    // ----- helpers -----

    private ChatRoomView toRoomView(ChatRoom room, Long accountId) {
        NegotiationParties parties = chatDirectoryPort.findPartiesByNegotiationId(room.getNegotiationId())
                .orElse(null);
        String projectTitle = parties != null ? parties.projectTitle() : null;
        String counterpartName = counterpartName(room, accountId, parties);

        ChatMessage last = chatMessageRepository.findLastMessage(room.getId()).orElse(null);
        return new ChatRoomView(room, projectTitle, counterpartName,
                last != null ? last.getContent() : null,
                last != null ? last.getCreatedAt() : null,
                unreadFor(room, accountId));
    }

    /** 상대 표시명. parties 의 계정 매칭으로 고른다(뷰어가 클라면 프리 이름, 반대면 회사명). */
    private String counterpartName(ChatRoom room, Long accountId, NegotiationParties parties) {
        if (parties == null) {
            return null;
        }
        if (accountId.equals(parties.clientAccountId())) {
            return parties.freelancerName();
        }
        if (accountId.equals(parties.freelancerAccountId())) {
            return parties.clientName();
        }
        // 방 참여자로는 확인됐지만 parties 계정과 안 맞는 예외적 경우: 다른 참여자 계정으로 이름 조회.
        return room.counterpartOf(accountId)
                .map(ChatRoomMember::getAccountId)
                .flatMap(chatDirectoryPort::findDisplayName)
                .orElse(null);
    }

    private int unreadFor(ChatRoom room, Long accountId) {
        return room.findMember(accountId)
                .map(member -> chatMessageRepository.countUnread(room.getId(), member.getLastReadAt(), accountId))
                .orElse(0);
    }

    private String senderName(ChatMessage message, Map<Long, String> cache) {
        Long senderId = message.getSenderId();
        if (senderId == null) {
            return null;   // 시스템 메시지
        }
        return cache.computeIfAbsent(senderId,
                id -> chatDirectoryPort.findDisplayName(id).orElse(null));
    }

    private ChatRoom load(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
