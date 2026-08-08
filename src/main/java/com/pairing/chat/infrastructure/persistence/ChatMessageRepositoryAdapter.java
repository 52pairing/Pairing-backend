package com.pairing.chat.infrastructure.persistence;

import com.pairing.chat.domain.model.ChatMessage;
import com.pairing.chat.domain.repository.ChatMessageRepository;
import com.pairing.chat.infrastructure.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryAdapter implements ChatMessageRepository {

    private final SpringDataChatMessageRepository springDataRepository;
    private final ChatMessageMapper mapper;

    @Override
    public ChatMessage save(ChatMessage message) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpaEntity(message)));
    }

    @Override
    public Page<ChatMessage> findByChatRoomId(Long chatRoomId, Pageable pageable) {
        return springDataRepository.findByChatRoomId(chatRoomId, pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<ChatMessage> findLastMessage(Long chatRoomId) {
        return springDataRepository.findFirstByChatRoomIdOrderByCreatedAtDescIdDesc(chatRoomId)
                .map(mapper::toDomain);
    }

    @Override
    public int countUnread(Long chatRoomId, LocalDateTime lastReadAt, Long accountId) {
        return (int) springDataRepository.countUnread(chatRoomId, lastReadAt, accountId);
    }
}
