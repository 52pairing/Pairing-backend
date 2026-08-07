package com.pairing.chat.infrastructure.persistence;

import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.chat.infrastructure.mapper.ChatRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryAdapter implements ChatRoomRepository {

    private final SpringDataChatRoomRepository springDataRepository;
    private final ChatRoomMapper mapper;

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpaEntity(chatRoom)));
    }

    @Override
    public Optional<ChatRoom> findById(Long chatRoomId) {
        return springDataRepository.findWithMembersById(chatRoomId).map(mapper::toDomain);
    }

    @Override
    public Optional<ChatRoom> findByNegotiationId(Long negotiationId) {
        return springDataRepository.findByNegotiationId(negotiationId).map(mapper::toDomain);
    }

    @Override
    public List<ChatRoom> findActiveRoomsByAccountId(Long accountId) {
        return springDataRepository.findActiveRoomsByAccountId(accountId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
