package com.pairing.freelancer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** resume_draft 테이블 매핑. 화면 입력값을 JSON 문자열로 통째로 보관한다. */
@Entity
@Table(name = "resume_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeDraftJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ResumeDraftJpaEntity(Long id, Long accountId, String payload, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }
}
