package com.pairing.support.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.support.exception.ChatbotErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 계정별 하루 챗봇 질의 사용량. 날짜가 바뀌면 새 행이 생겨서 자동으로 초기화된다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotQuota {

    public static final int DAILY_LIMIT = 10;

    private Long id;
    private Long accountId;
    private LocalDate quotaDate;
    private int usedCount;

    private ChatbotQuota(Long id, Long accountId, LocalDate quotaDate, int usedCount) {
        this.id = id;
        this.accountId = accountId;
        this.quotaDate = quotaDate;
        this.usedCount = usedCount;
    }

    public static ChatbotQuota createForToday(Long accountId) {
        return new ChatbotQuota(null, accountId, LocalDate.now(), 0);
    }

    public static ChatbotQuota reconstitute(Long id, Long accountId, LocalDate quotaDate, int usedCount) {
        return new ChatbotQuota(id, accountId, quotaDate, usedCount);
    }

    /** 한도를 넘겼으면 {@code CB_003}. AI 호출 전에 먼저 불러서 실패를 빨리 감지한다. */
    public void increment() {
        if (usedCount >= DAILY_LIMIT) {
            throw new BusinessException(ChatbotErrorCode.QUOTA_EXCEEDED);
        }
        usedCount++;
    }

    public int remaining() {
        return DAILY_LIMIT - usedCount;
    }
}
