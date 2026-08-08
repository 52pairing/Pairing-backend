package com.pairing.review.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.exception.ReviewErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사이트(플랫폼) 이용 후기. (요구사항 R22, R40)
 *
 * <p>기본값은 비공개다. 관리자가 확인 후 공개·홍보 여부를 바꾼다({@link #updateVisibility}).
 * 그 외 내용은 작성 후 바뀌지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteReview {

    private Long id;
    private Long contractId;
    private Long projectId;
    private Long writerAccountId;
    private PartyRole writerRole;
    private int score;
    private String content;
    private SiteReviewVisibility visibility;
    private boolean promoted;
    private LocalDateTime createdAt;

    private SiteReview(Long id, Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                       int score, String content, SiteReviewVisibility visibility, boolean promoted,
                       LocalDateTime createdAt) {
        if (contractId == null || projectId == null || writerAccountId == null || writerRole == null) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        if (score < 1 || score > 5) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        this.id = id;
        this.contractId = contractId;
        this.projectId = projectId;
        this.writerAccountId = writerAccountId;
        this.writerRole = writerRole;
        this.score = score;
        this.content = content;
        this.visibility = visibility;
        this.promoted = promoted;
        this.createdAt = createdAt;
    }

    public static SiteReview create(Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                                    int score, String content) {
        return new SiteReview(null, contractId, projectId, writerAccountId, writerRole, score, content,
                SiteReviewVisibility.PRIVATE, false, LocalDateTime.now());
    }

    public static SiteReview reconstitute(Long id, Long contractId, Long projectId, Long writerAccountId,
                                          PartyRole writerRole, int score, String content,
                                          SiteReviewVisibility visibility, boolean promoted,
                                          LocalDateTime createdAt) {
        return new SiteReview(id, contractId, projectId, writerAccountId, writerRole, score, content, visibility,
                promoted, createdAt);
    }

    /** [관리자] 공개·홍보 설정 변경. */
    public void updateVisibility(SiteReviewVisibility visibility, boolean promoted) {
        if (visibility == null) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        this.visibility = visibility;
        this.promoted = promoted;
    }
}
