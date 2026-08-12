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

    /**
     * 감사 기록. 해시 체인에는 들어가지만 <b>당사자 협상방에는 보이지 않는다</b>
     * ({@link #isAudit()} 로 걸러진다).
     *
     * <p>타결 시점 최종 조건 스냅샷처럼 <b>기계가 파싱할 증거</b>를 남길 때 쓴다. 사람에게
     * 보여 줄 내용이면 {@link #system} 을 쓸 것.
     *
     * <p><b>왜 별도 messageType 이 아니라 내용 표식인가.</b> RDS 에
     * {@code message_type CHECK IN ('PROPOSAL','RESPONSE','SYSTEM')} 제약이 걸려 있어서
     * {@code AUDIT} 같은 값을 넣으면 INSERT 가 거부되고 <b>타결 트랜잭션이 통째로 롤백된다.</b>
     * 제약을 푸는 마이그레이션은 팀이 함께 쓰는 DB 를 건드리는 일이라 따로 잡아야 한다.
     * 그때까지는 표식으로 구분하되, <b>판정 로직을 여기 한 곳에 가둔다</b> — 호출부는
     * {@link #isAudit()} 만 부르므로 나중에 타입으로 바꿔도 호출부는 그대로다.
     */
    public static NegotiationMessage audit(Long negotiationId, int roundNo, String body) {
        return new NegotiationMessage(null, negotiationId, null, roundNo, SenderType.SYSTEM,
                NegotiationMessageType.SYSTEM, AUDIT_MARKER + body, null, null, null, null, null, null,
                LocalDateTime.now());
    }

    /**
     * 당사자 화면에서 감춰야 하는 감사 기록인가.
     *
     * <p>표식은 {@link #audit} 이 붙인다. 사람이 쓴 내용과 겹치지 않도록 유닛 세퍼레이터를 쓰므로
     * 사용자가 우연히 같은 문자열을 입력할 수 없다.
     */
    public boolean isAudit() {
        return content != null && content.startsWith(AUDIT_MARKER);
    }

    /** 감사 기록 표식. 화면에 안 나가고 해시에만 포함되므로 눈에 띌 필요가 없다. */
    private static final String AUDIT_MARKER = String.valueOf((char) 0x1E);

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
