package com.pairing.chat.application.service;

import com.pairing.chat.application.event.ChatMessageBroadcast;
import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.chat.application.port.out.ChatDirectoryPort.NegotiationParties;
import com.pairing.chat.application.port.out.ChatEventPort;
import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.chat.application.usecase.ChatCommandUseCase;
import com.pairing.chat.domain.model.ChatMemberRole;
import com.pairing.chat.domain.model.ChatMessage;
import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.model.ChatRoomMember;
import com.pairing.chat.domain.repository.ChatMessageRepository;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatCommandService implements ChatCommandUseCase, ChatActivationUseCase {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatDirectoryPort chatDirectoryPort;
    private final ChatEventPort chatEventPort;

    @Override
    public void provisionForAgreedNegotiation(Long negotiationId) {
        // 멱등: 이미 방이 있으면 아무 것도 하지 않는다(재타결 호출·재시도 대비).
        if (chatRoomRepository.findByNegotiationId(negotiationId).isPresent()) {
            return;
        }

        NegotiationParties parties = chatDirectoryPort.findPartiesByNegotiationId(negotiationId)
                .orElseThrow(() -> new IllegalStateException(
                        "채팅방을 열 수 없습니다. 협상 당사자 정보를 찾을 수 없습니다. negotiationId=" + negotiationId));

        List<ChatRoomMember> members = List.of(
                ChatRoomMember.join(parties.clientAccountId(), ChatMemberRole.CLIENT),
                ChatRoomMember.join(parties.freelancerAccountId(), ChatMemberRole.FREELANCER));

        // 입력창은 잠긴 채로 열린다(계약 체결 후 활성화). 안내 문구도 그에 맞춘다.
        ChatRoom saved = chatRoomRepository.save(ChatRoom.open(negotiationId, members));
        chatMessageRepository.save(ChatMessage.system(saved.getId(),
                "협상이 타결되었습니다. 계약이 체결되면 대화를 시작할 수 있습니다."));
    }

    @Override
    public void enableInputForSignedContract(Long negotiationId) {
        ChatRoom room = chatRoomRepository.findByNegotiationId(negotiationId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        // 멱등: 이미 열려 있으면 안내 메시지를 다시 남기지 않는다(재호출·재시도 대비).
        if (!room.enableInput()) {
            return;
        }
        chatRoomRepository.save(room);
        chatMessageRepository.save(ChatMessage.system(room.getId(),
                "계약이 체결되었습니다. 이제 자유롭게 대화해 주세요."));
    }

    @Override
    public ChatMessageView sendMessage(Long chatRoomId, Long accountId, String content) {
        ChatRoom room = load(chatRoomId);
        room.ensureCanSend(accountId);

        ChatMessage saved = chatMessageRepository.save(ChatMessage.text(chatRoomId, accountId, content));

        // 보낸 사람은 방금 자기 메시지까지 본 것으로 처리한다(안읽음 배지에 자기 메시지가 잡히지 않게).
        room.findMember(accountId).ifPresent(member -> member.markRead(saved.getCreatedAt()));
        chatRoomRepository.save(room);

        String senderName = chatDirectoryPort.findDisplayName(accountId).orElse(null);
        chatEventPort.publish(new ChatMessageBroadcast(chatRoomId, saved.getId(), accountId, senderName,
                saved.getMessageType(), saved.getContent(), saved.getCreatedAt()));

        return new ChatMessageView(saved, senderName, true);
    }

    @Override
    public void markAsRead(Long chatRoomId, Long accountId) {
        ChatRoom room = load(chatRoomId);
        ChatRoomMember member = room.requireActiveMember(accountId);
        member.markRead(LocalDateTime.now());
        chatRoomRepository.save(room);
    }

    @Override
    public void leave(Long chatRoomId, Long accountId) {
        ChatRoom room = load(chatRoomId);
        ChatRoomMember member = room.requireActiveMember(accountId);
        if (!room.isLeaveAllowed()) {
            throw new BusinessException(ChatErrorCode.LEAVE_NOT_ALLOWED);
        }
        member.leave(LocalDateTime.now());
        chatRoomRepository.save(room);
    }

    private ChatRoom load(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
