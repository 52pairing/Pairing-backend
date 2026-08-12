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
 * <p><b>기본값은 공개다.</b> 후기는 대부분 문제가 없는데 관리자가 하나하나 열어 공개로 바꾸면
 * 그 일이 밀리는 동안 아무 후기도 노출되지 않는다. 그래서 공개로 두고, 부적절한 내용이 보이면
 * 그때 비공개로 내린다({@link #updateVisibility}).
 *
 * <p>공개라고 바로 메인에 뜨는 것은 아니다. 메인 노출은 관리자가 홍보 활용까지 켜야 한다.
 * 즉 <b>공개는 기본값, 홍보는 선별</b>이다.
 *
 * <p>그 외 내용은 작성 후 바뀌지 않는다.
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
                SiteReviewVisibility.PUBLIC, false, LocalDateTime.now());
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
