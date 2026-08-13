package com.pairing.support.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.global.exception.BusinessException;
import com.pairing.support.application.command.AskChatbotCommand;
import com.pairing.support.application.port.out.ChatbotAiPort;
import com.pairing.support.application.result.ChatbotAnswerResult;
import com.pairing.support.application.result.ChatbotQuotaResult;
import com.pairing.support.application.usecase.ChatbotUseCase;
import com.pairing.support.domain.model.ChatbotIntent;
import com.pairing.support.domain.model.ChatbotMessage;
import com.pairing.support.domain.model.ChatbotQuota;
import com.pairing.support.domain.model.ChatbotSession;
import com.pairing.support.domain.repository.ChatbotMessageRepository;
import com.pairing.support.domain.repository.ChatbotQuotaRepository;
import com.pairing.support.domain.repository.ChatbotSessionRepository;
import com.pairing.support.exception.ChatbotErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatbotService implements ChatbotUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ChatbotSessionRepository sessionRepository;
    private final ChatbotMessageRepository messageRepository;
    private final ChatbotQuotaRepository quotaRepository;
    private final ChatbotAiPort chatbotAiPort;

    @Override
    @Transactional
    public ChatbotAnswerResult ask(AskChatbotCommand command) {
        ChatbotQuota quota = findOrCreateTodayQuota(command.accountId());
        quota.increment(); // 한도 초과면 AI 호출 전에 예외로 걸러진다.

        ChatbotSession session = resolveSession(command.accountId(), command.sessionId());
        ChatbotAiPort.Answer aiAnswer = chatbotAiPort.ask(command.question());

        // 역할에 맞지 않는 화면이면 버튼만 뺀다. 답변은 그대로 나간다.
        // 걸러낸 결과를 저장한다 — 화면에 실제로 띄운 것이 이력에도 그대로 남아야 한다.
        ChatbotIntent intent = ChatbotIntent.from(aiAnswer.intent())
                .filterFor(accountQueryUseCase.getById(command.accountId()).getRole());

        ChatbotMessage saved = messageRepository.save(
                ChatbotMessage.create(session.getId(), command.question(), aiAnswer.answer(), intent));

        // 페어링과 무관한 질문이면 사용량을 차감하지 않는다. 답을 못 받았는데 횟수만 빠지면
        // 오타 한 번에 하루 10회 중 1회가 날아간다. quota 는 위에서 메모리로만 증가시켰으므로
        // 저장하지 않으면 그대로 없던 일이 된다.
        //
        // 대화 자체는 남긴다. 새로고침했을 때 방금 한 질문이 사라지면 그게 더 이상하다.
        if (aiAnswer.outOfScope()) {
            return new ChatbotAnswerResult(session.getId(), saved.getQuestion(), saved.getAnswer(),
                    saved.getIntent(), getQuota(command.accountId()).remainingCount(),
                    saved.getCreatedAt());
        }

        // AI 호출이 성공했을 때만 사용량을 반영한다.
        ChatbotQuota persistedQuota = saveQuotaSafely(command.accountId(), quota);

        return new ChatbotAnswerResult(session.getId(), saved.getQuestion(), saved.getAnswer(),
                saved.getIntent(), persistedQuota.remaining(), saved.getCreatedAt());
    }

    /**
     * 오늘자 quota 행이 없어서 새로 만든 경우, 동시에 들어온 다른 요청이 먼저 그 행을 만들어버리면
     * INSERT 가 유니크 제약(계정+날짜) 위반으로 실패한다. 이때는 그 요청이 만든 행을 다시 읽어 증가시킨다.
     */
    private ChatbotQuota saveQuotaSafely(Long accountId, ChatbotQuota quota) {
        try {
            return quotaRepository.save(quota);
        } catch (DataIntegrityViolationException e) {
            ChatbotQuota existing = quotaRepository.findByAccountIdAndQuotaDate(accountId, LocalDate.now())
                    .orElseThrow(() -> e);
            existing.increment();
            return quotaRepository.save(existing);
        }
    }

    @Override
    public ChatbotQuotaResult getQuota(Long accountId) {
        LocalDate today = LocalDate.now();
        int usedCount = quotaRepository.findByAccountIdAndQuotaDate(accountId, today)
                .map(ChatbotQuota::getUsedCount)
                .orElse(0);
        return new ChatbotQuotaResult(today, ChatbotQuota.DAILY_LIMIT, usedCount,
                ChatbotQuota.DAILY_LIMIT - usedCount);
    }

    @Override
    public List<ChatbotAnswerResult> findTodayMessages(Long accountId) {
        int remaining = getQuota(accountId).remainingCount();
        return messageRepository.findByAccountIdAndDate(accountId, LocalDate.now()).stream()
                // 버튼도 함께 복원한다. 버튼을 눌러 이동했다가 돌아와도 그대로 남아 있어야 한다.
                .map(message -> new ChatbotAnswerResult(message.getSessionId(), message.getQuestion(),
                        message.getAnswer(), message.getIntent(), remaining, message.getCreatedAt()))
                .toList();
    }

    private ChatbotQuota findOrCreateTodayQuota(Long accountId) {
        return quotaRepository.findByAccountIdAndQuotaDate(accountId, LocalDate.now())
                .orElseGet(() -> ChatbotQuota.createForToday(accountId));
    }

    private ChatbotSession resolveSession(Long accountId, Long sessionId) {
        if (sessionId == null) {
            return sessionRepository.save(ChatbotSession.create(accountId));
        }
        return getOwnedSession(accountId, sessionId);
    }

    private ChatbotSession getOwnedSession(Long accountId, Long sessionId) {
        ChatbotSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ChatbotErrorCode.SESSION_NOT_FOUND));
        if (!session.isOwnedBy(accountId)) {
            throw new BusinessException(ChatbotErrorCode.SESSION_FORBIDDEN);
        }
        return session;
    }
}
