package com.pairing.negotiation.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 협상 로그 1건. AI 제안·근거와 사람 응답, 시스템 안내가 시간순으로 남는다(R12, 삭제 안 함).
 * Negotiation 애그리거트와 생명주기를 공유하지 않는 별도 엔티티다(고빈도).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationMessage {

    private Long id;
    private Long negotiationId;
    private Long conditionId;      // 조건과 무관한 안내는 null
    private int roundNo;
    private SenderType senderType;
    private NegotiationMessageType messageType;
    private String content;
    private String reason;         // AI 제안 근거
    private String proposedValue;  // 제안값
    private String response;       // 사람 응답 결과(YES/NO/직접입력값)
    private LocalDateTime createdAt;

    private NegotiationMessage(Long id, Long negotiationId, Long conditionId, int roundNo,
                               SenderType senderType, NegotiationMessageType messageType, String content,
                               String reason, String proposedValue, String response, LocalDateTime createdAt) {
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

    /** AI(또는 stub 심판)의 조건별 제안. 근거가 반드시 붙는다. */
    public static NegotiationMessage proposal(Long negotiationId, Long conditionId, int roundNo,
                                              SenderType senderType, String content, String reason,
                                              String proposedValue) {
        return new NegotiationMessage(null, negotiationId, conditionId, roundNo, senderType,
                NegotiationMessageType.PROPOSAL, content, reason, proposedValue, null, LocalDateTime.now());
    }

    /** 사람의 조건별 응답(수락/거절+직접입력값). */
    public static NegotiationMessage response(Long negotiationId, Long conditionId, int roundNo,
                                              SenderType senderType, String content, String response) {
        return new NegotiationMessage(null, negotiationId, conditionId, roundNo, senderType,
                NegotiationMessageType.RESPONSE, content, null, null, response, LocalDateTime.now());
    }

    /** 시스템 안내(타결·결렬 등). */
    public static NegotiationMessage system(Long negotiationId, int roundNo, String content) {
        return new NegotiationMessage(null, negotiationId, null, roundNo, SenderType.SYSTEM,
                NegotiationMessageType.SYSTEM, content, null, null, null, LocalDateTime.now());
    }

    public static NegotiationMessage reconstitute(Long id, Long negotiationId, Long conditionId, int roundNo,
                                                  SenderType senderType, NegotiationMessageType messageType,
                                                  String content, String reason, String proposedValue,
                                                  String response, LocalDateTime createdAt) {
        return new NegotiationMessage(id, negotiationId, conditionId, roundNo, senderType, messageType,
                content, reason, proposedValue, response, createdAt);
    }
}
