package com.pairing.negotiation.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * 협상 로그 1건 = 분쟁 대비 증거. AI 제안·근거, 사람 응답, 시스템 안내가 시간순으로 남는다
 * (R12, 수정·삭제 안 함 = append-only). Negotiation 애그리거트와 별도 엔티티다(고빈도).
 *
 * <p>위변조 탐지: 각 로그는 {@code contentHash = SHA-256(증거필드 + 직전 로그 해시)} 로 체인을 이룬다.
 * 과거 한 줄만 조작해도 이후 해시가 전부 어긋나 검증에서 드러난다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationMessage {

    /** 체인 첫 로그의 직전 해시(제네시스). */
    public static final String GENESIS_HASH = "GENESIS";

    /** 해시 정규화 시 필드 구분자. 유닛 세퍼레이터(U+001F)라 사람이 쓴 내용과 안 섞인다. */
    private static final String FIELD_SEPARATOR = String.valueOf((char) 0x1F);

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
    private Long actingAccountId;  // 사람 행위자 계정(부인방지). 제안/시스템은 null
    private String prevHash;       // 직전 로그의 contentHash (체인 연결)
    private String contentHash;    // 이 로그의 해시 (seal 시 계산)
    private LocalDateTime createdAt;

    private NegotiationMessage(Long id, Long negotiationId, Long conditionId, int roundNo,
                               SenderType senderType, NegotiationMessageType messageType, String content,
                               String reason, String proposedValue, String response, Long actingAccountId,
                               String prevHash, String contentHash, LocalDateTime createdAt) {
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

    /** AI(또는 stub 심판)의 조건별 제안. 근거가 반드시 붙는다. 행위자 없음(AI). */
    public static NegotiationMessage proposal(Long negotiationId, Long conditionId, int roundNo,
                                              SenderType senderType, String content, String reason,
                                              String proposedValue) {
        return new NegotiationMessage(null, negotiationId, conditionId, roundNo, senderType,
                NegotiationMessageType.PROPOSAL, content, reason, proposedValue, null, null, null, null,
                LocalDateTime.now());
    }

    /** 사람의 조건별 응답(수락/거절+직접입력값). actingAccountId = 실제 응답한 계정(부인방지). */
    public static NegotiationMessage response(Long negotiationId, Long conditionId, int roundNo,
                                              SenderType senderType, String content, String response,
                                              Long actingAccountId) {
        return new NegotiationMessage(null, negotiationId, conditionId, roundNo, senderType,
                NegotiationMessageType.RESPONSE, content, null, null, response, actingAccountId, null, null,
                LocalDateTime.now());
    }

    /** 시스템 안내(타결·결렬 등). */
    public static NegotiationMessage system(Long negotiationId, int roundNo, String content) {
        return new NegotiationMessage(null, negotiationId, null, roundNo, SenderType.SYSTEM,
                NegotiationMessageType.SYSTEM, content, null, null, null, null, null, null,
                LocalDateTime.now());
    }

    public static NegotiationMessage reconstitute(Long id, Long negotiationId, Long conditionId, int roundNo,
                                                  SenderType senderType, NegotiationMessageType messageType,
                                                  String content, String reason, String proposedValue,
                                                  String response, Long actingAccountId, String prevHash,
                                                  String contentHash, LocalDateTime createdAt) {
        return new NegotiationMessage(id, negotiationId, conditionId, roundNo, senderType, messageType,
                content, reason, proposedValue, response, actingAccountId, prevHash, contentHash, createdAt);
    }

    /** 체인에 봉인: 직전 해시를 걸고 이 로그의 해시를 계산한다. 저장 직전에 한 번 호출한다. */
    public void seal(String prevHashValue) {
        this.prevHash = prevHashValue;
        this.contentHash = computeHash(prevHashValue);
    }

    /** 저장된 필드로 해시를 재계산한다(검증용). 저장값과 다르면 위변조. */
    public String recomputeHash() {
        return computeHash(this.prevHash);
    }

    private String computeHash(String prevHashValue) {
        String canonical = String.join(FIELD_SEPARATOR,
                str(negotiationId), str(conditionId), Integer.toString(roundNo),
                str(senderType), str(messageType), nz(content), nz(reason), nz(proposedValue),
                nz(response), str(actingAccountId), str(createdAt), nz(prevHashValue));
        return sha256Hex(canonical);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);  // JVM 표준, 발생 불가
        }
    }
}
