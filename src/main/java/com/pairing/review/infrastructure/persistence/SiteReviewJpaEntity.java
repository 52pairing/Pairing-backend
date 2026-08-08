package com.pairing.review.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReviewVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "site_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "writer_account_id", nullable = false)
    private Long writerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_role", nullable = false, length = 10)
    private PartyRole writerRole;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "content", length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private SiteReviewVisibility visibility;

    @Column(name = "promoted", nullable = false)
    private boolean promoted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SiteReviewJpaEntity(Long id, Long contractId, Long projectId, Long writerAccountId,
                               PartyRole writerRole, int score, String content, SiteReviewVisibility visibility,
                               boolean promoted, LocalDateTime createdAt) {
        this.id = id;
        this.contractId = contractId;
        this.projectId = projectId;
        this.writerAccountId = writerAccountId;
        this.writerRole = writerRole;
        this.score = score;
        this.content = content;
        this.visibility = visibility;
        this.promoted = promoted;
        this.createdAt = createdAt;
    }
}
