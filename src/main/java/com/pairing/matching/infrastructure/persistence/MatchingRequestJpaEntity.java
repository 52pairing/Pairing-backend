package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.RejectReason;
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

/**
 * matching_request 테이블 매핑.
 *
 * <p>requested_at/expires_at은 응답 기한(3일) 계산에 바로 쓰여야 해서 명시적으로 매핑한다.
 * created_at/updated_at(순수 부기용)은 매핑하지 않는다(account 도메인과 동일 컨벤션).
 */
@Entity
@Table(name = "matching_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MatchingStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 255)
    private RejectReason rejectReason;

    public MatchingRequestJpaEntity(Long id, Long projectId, Long positionId, Long candidateId, Long freelancerId,
                                    MatchingStatus status, LocalDateTime requestedAt, LocalDateTime expiresAt,
                                    LocalDateTime respondedAt, RejectReason rejectReason) {
        this.id = id;
        this.projectId = projectId;
        this.positionId = positionId;
        this.candidateId = candidateId;
        this.freelancerId = freelancerId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
        this.respondedAt = respondedAt;
        this.rejectReason = rejectReason;
    }
}
