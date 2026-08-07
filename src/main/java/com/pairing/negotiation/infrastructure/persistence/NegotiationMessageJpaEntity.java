package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.SenderType;
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
@Table(name = "negotiation_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(name = "condition_id")
    private Long conditionId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private NegotiationMessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "proposed_value")
    private String proposedValue;

    @Column(name = "response", length = 20)
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public NegotiationMessageJpaEntity(Long id, Long negotiationId, Long conditionId, int roundNo,
                                       SenderType senderType, NegotiationMessageType messageType, String content,
                                       String reason, String proposedValue, String response,
                                       LocalDateTime createdAt) {
        this.id = id;
        this.negotiationId = negotiationId;
        this.conditionId = conditionId;
        this.roundNo = roundNo;
        this.senderType = senderType;
        this.messageType = messageType;
        this.content = content;
        this.reason = reason;
        this.proposedValue = proposedValue;
        this.response = response;
        this.createdAt = createdAt;
    }
}
