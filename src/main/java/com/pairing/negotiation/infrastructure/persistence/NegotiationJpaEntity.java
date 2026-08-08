package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "negotiation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NegotiationStatus status;

    @Column(name = "total_round", nullable = false)
    private int totalRound;

    @Column(name = "agreed_amount")
    private Long agreedAmount;

    @Column(name = "budget_cap", nullable = false)
    private Long budgetCap;

    @Column(name = "floor_amount", nullable = false)
    private Long floorAmount;

    @Column(name = "ai_out_at")
    private LocalDateTime aiOutAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "client_last_read_at")
    private LocalDateTime clientLastReadAt;

    @Column(name = "freelancer_last_read_at")
    private LocalDateTime freelancerLastReadAt;

    // 애그리거트: 조건은 협상과 생명주기를 함께한다(cascade + orphanRemoval).
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "negotiation_id")
    private List<NegotiationConditionJpaEntity> conditions = new ArrayList<>();

    public NegotiationJpaEntity(Long id, Long requestId, Long projectId, Long positionId, Long freelancerId,
                                NegotiationStatus status, int totalRound, Long agreedAmount, Long budgetCap,
                                Long floorAmount, LocalDateTime aiOutAt, LocalDateTime startedAt,
                                LocalDateTime endedAt, String endReason, LocalDateTime clientLastReadAt,
                                LocalDateTime freelancerLastReadAt,
                                List<NegotiationConditionJpaEntity> conditions) {
        this.id = id;
        this.requestId = requestId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.status = status;
        this.totalRound = totalRound;
        this.agreedAmount = agreedAmount;
        this.budgetCap = budgetCap;
        this.floorAmount = floorAmount;
        this.aiOutAt = aiOutAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
        this.clientLastReadAt = clientLastReadAt;
        this.freelancerLastReadAt = freelancerLastReadAt;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
