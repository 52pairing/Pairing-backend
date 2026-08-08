package com.pairing.matching.domain.model;

import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 매칭 요청 1건. 인원별(포지션 단위) 진행 상태를 갖는다(R19).
 *
 * <p>REQUEST_PENDING에서 시작해 ACCEPTED/REJECTED로 응답 기한(3일) 안에 갈리고,
 * 그 이후 협상·계약 단계 전환은 negotiation/contract 도메인이 담당한다(advanceStatus로 반영).
 * REJECTED / NEGOTIATION_FAILED / TERMINATED / CLOSED 는 종결 상태이며 되돌리지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRequest {

    private static final int RESPONSE_DEADLINE_DAYS = 3;

    private Long id;
    private Long projectId;
    private Long positionId;
    private Long candidateId;
    private Long freelancerId;
    private MatchingStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;
    private RejectReason rejectReason;

    private MatchingRequest(Long id, Long projectId, Long positionId, Long candidateId, Long freelancerId,
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

    /** 매칭 요청을 발송한다. 응답 기한은 발송 시점 +3일이다. */
    public static MatchingRequest create(Long projectId, Long positionId, Long candidateId, Long freelancerId) {
        if (projectId == null || positionId == null || candidateId == null || freelancerId == null) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        LocalDateTime now = LocalDateTime.now();
        return new MatchingRequest(null, projectId, positionId, candidateId, freelancerId,
                MatchingStatus.REQUEST_PENDING, now, now.plusDays(RESPONSE_DEADLINE_DAYS), null, null);
    }

    public static MatchingRequest reconstitute(Long id, Long projectId, Long positionId, Long candidateId,
                                               Long freelancerId, MatchingStatus status, LocalDateTime requestedAt,
                                               LocalDateTime expiresAt, LocalDateTime respondedAt,
                                               RejectReason rejectReason) {
        return new MatchingRequest(id, projectId, positionId, candidateId, freelancerId, status, requestedAt,
                expiresAt, respondedAt, rejectReason);
    }

    /** 프리랜서가 수락한다. 협상방 생성은 서비스 계층이 같은 트랜잭션에서 이어서 처리한다. */
    public void accept() {
        assertPending();
        this.status = MatchingStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    /** 프리랜서가 직접 거절한다. */
    public void reject() {
        assertPending();
        this.status = MatchingStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
        this.rejectReason = RejectReason.DIRECT_REJECT;
    }

    /** 응답 기한(3일)을 넘겨 시스템이 자동으로 만료 처리한다. 거절과 동일한 상태지만 사유로 구분한다. */
    public void expire() {
        assertPending();
        this.status = MatchingStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
        this.rejectReason = RejectReason.EXPIRED;
    }

    /** 협상 결렬로 이 매칭 요청 건이 종결됨을 반영한다(협상 도메인이 호출). */
    public void failNegotiation() {
        if (this.status != MatchingStatus.NEGOTIATING) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        this.status = MatchingStatus.NEGOTIATION_FAILED;
        this.rejectReason = RejectReason.NEGOTIATION_FAILED;
    }

    /** 협상 타결로 계약 대기 단계로 넘어감을 반영한다(협상 도메인이 호출). */
    public void agreeNegotiation() {
        if (this.status != MatchingStatus.NEGOTIATING) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        this.status = MatchingStatus.CONTRACT_PENDING;
    }

    /** 협상/계약 도메인이 다음 단계로 넘어갔음을 반영할 때 쓴다(예: ACCEPTED -&gt; NEGOTIATING). */
    public void advanceStatus(MatchingStatus next) {
        if (isTerminal(this.status)) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        this.status = next;
    }

    public boolean isExpired() {
        return this.status == MatchingStatus.REQUEST_PENDING && LocalDateTime.now().isAfter(this.expiresAt);
    }

    private void assertPending() {
        if (this.status != MatchingStatus.REQUEST_PENDING) {
            throw new BusinessException(MatchingErrorCode.ALREADY_RESPONDED);
        }
    }

    private boolean isTerminal(MatchingStatus status) {
        return status == MatchingStatus.REJECTED || status == MatchingStatus.NEGOTIATION_FAILED
                || status == MatchingStatus.TERMINATED || status == MatchingStatus.CLOSED;
    }
}
