package com.pairing.review.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "review", uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_contract_reviewer", columnNames = {"contract_id", "reviewer_account_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "reviewer_account_id", nullable = false)
    private Long reviewerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false, length = 10)
    private PartyRole reviewerRole;

    @Column(name = "reviewee_account_id", nullable = false)
    private Long revieweeAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewee_role", nullable = false, length = 10)
    private PartyRole revieweeRole;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReviewJpaEntity(Long id, Long contractId, Long projectId, Long reviewerAccountId,
                           PartyRole reviewerRole, Long revieweeAccountId, PartyRole revieweeRole, int score,
                           String content, LocalDateTime createdAt) {
        this.id = id;
        this.contractId = contractId;
        this.projectId = projectId;
        this.reviewerAccountId = reviewerAccountId;
        this.reviewerRole = reviewerRole;
        this.revieweeAccountId = revieweeAccountId;
        this.revieweeRole = revieweeRole;
        this.score = score;
        this.content = content;
        this.createdAt = createdAt;
    }
}
