package com.pairing.chat.infrastructure.negotiation;

import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.negotiation.application.port.out.ChatRoomLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 협상 도메인의 {@link ChatRoomLookupPort} 를 채팅 도메인이 구현한다. 협상당 채팅방은 하나
 * (negotiation_id UNIQUE)라 있으면 그 ID 를 돌려준다. 생성 어댑터({@code ChatRoomCreationAdapter})의 읽기 짝.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomLookupAdapter implements ChatRoomLookupPort {

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Optional<Long> findChatRoomIdByNegotiationId(Long negotiationId) {
        return chatRoomRepository.findByNegotiationId(negotiationId).map(ChatRoom::getId);
    }
}
