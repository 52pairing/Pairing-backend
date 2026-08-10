package com.pairing.support.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "chatbot_quota", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chatbot_quota_account_date", columnNames = {"account_id", "quota_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotQuotaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    public ChatbotQuotaJpaEntity(Long id, Long accountId, LocalDate quotaDate, int usedCount) {
        this.id = id;
        this.accountId = accountId;
        this.quotaDate = quotaDate;
        this.usedCount = usedCount;
    }
}
