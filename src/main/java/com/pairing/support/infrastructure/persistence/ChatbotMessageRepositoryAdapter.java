package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.ChatbotMessage;
import com.pairing.support.domain.repository.ChatbotMessageRepository;
import com.pairing.support.infrastructure.mapper.ChatbotMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatbotMessageRepositoryAdapter implements ChatbotMessageRepository {

    private final SpringDataChatbotMessageRepository springDataRepository;
    private final ChatbotMessageMapper chatbotMessageMapper;

    @Override
    public ChatbotMessage save(ChatbotMessage message) {
        return chatbotMessageMapper.toDomain(springDataRepository.save(chatbotMessageMapper.toJpaEntity(message)));
    }

    @Override
    public List<ChatbotMessage> findByAccountIdAndDate(Long accountId, LocalDate date) {
        // createdAt 은 LocalDateTime 이라 날짜만으로는 못 거른다. 그 날 00:00 이상 ~ 다음 날 00:00 미만.
        return springDataRepository.findByOwnerAccountIdAndCreatedAtRange(
                        accountId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .map(chatbotMessageMapper::toDomain)
                .toList();
    }
}
