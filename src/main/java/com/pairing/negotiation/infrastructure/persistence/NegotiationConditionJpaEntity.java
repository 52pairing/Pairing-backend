package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "negotiation_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationConditionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 30)
    private ConditionType conditionType;

    @Column(name = "client_value")
    private String clientValue;

    @Column(name = "freelancer_value")
    private String freelancerValue;

    @Column(name = "client_floor")
    private String clientFloor;

    @Column(name = "freelancer_floor")
    private String freelancerFloor;

    @Column(name = "agreed_value")
    private String agreedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConditionStatus status;

    @Column(name = "round_count", nullable = false)
    private int roundCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    public NegotiationConditionJpaEntity(Long id, ConditionType conditionType, String clientValue,
                                         String freelancerValue, String clientFloor, String freelancerFloor,
                                         String agreedValue, ConditionStatus status, int roundCount,
                                         int sortOrder, LocalDateTime agreedAt) {
        this.id = id;
        this.conditionType = conditionType;
        this.clientValue = clientValue;
        this.freelancerValue = freelancerValue;
        this.clientFloor = clientFloor;
        this.freelancerFloor = freelancerFloor;
        this.agreedValue = agreedValue;
        this.status = status;
        this.roundCount = roundCount;
        this.sortOrder = sortOrder;
        this.agreedAt = agreedAt;
    }
}
