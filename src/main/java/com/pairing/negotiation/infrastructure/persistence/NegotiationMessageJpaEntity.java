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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
/*
 * 조회는 전부 negotiation_id 로 좁힌 뒤 (라운드 | 종류 | 생성시각)을 더하는 형태다. 인덱스가 없으면
 * 협상방 진입·목록 배지·헤더 응답대기 카운트가 매번 전체 스캔이 되고, 메시지는 협상 1건당
 * 라운드(최대 15) × 조건 수만큼 쌓여 금방 커진다.
 *
 *   idx_msg_nego_round : findByNegotiationIdOrderByRoundNoAscIdAsc(로그 조회),
 *                        countByNegotiationIdAndMessageTypeAndRoundNo,
 *                        countByNegotiationIdAndSenderTypeAndRoundNoAndMessageType,
 *                        waiting-count 의 EXISTS/NOT EXISTS 서브쿼리
 *   idx_msg_nego_created : countByNegotiationIdAndMessageTypeAndCreatedAtAfter(안 읽은 제안 수)
 */
@Table(name = "negotiation_message", indexes = {
        @Index(name = "idx_msg_nego_round", columnList = "negotiation_id, round_no, message_type"),
        @Index(name = "idx_msg_nego_created", columnList = "negotiation_id, message_type, created_at")
})
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

    @Column(name = "acting_account_id")
    private Long actingAccountId;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public NegotiationMessageJpaEntity(Long id, Long negotiationId, Long conditionId, int roundNo,
                                       SenderType senderType, NegotiationMessageType messageType, String content,
                                       String reason, String proposedValue, String response,
                                       Long actingAccountId, String prevHash, String contentHash,
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
        this.actingAccountId = actingAccountId;
        this.prevHash = prevHash;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
    }
}
