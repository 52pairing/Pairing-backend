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
 * <p>후기 원문은 <b>관리자 화면 밖으로 나가지 않는다.</b> 사용자가 보는 것은 관리자가 홍보로 고른
 * 후기와 평균 별점뿐이다. 그래서 노출 여부를 정하는 스위치는 {@code promoted} 하나다.
 *
 * <p>예전에는 공개/비공개를 따로 뒀는데, 홍보를 끄면 이미 안 보이는 상태라 아무것도 바꾸지 않는
 * 스위치였다. 관리자가 두 번 눌러야 했고 어긋난 조합까지 관리해야 해서 없앴다.
 *
 * <p>작성 후 내용과 별점은 바뀌지 않는다.
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
    private boolean promoted;
    private LocalDateTime createdAt;

    private SiteReview(Long id, Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                       int score, String content, boolean promoted, LocalDateTime createdAt) {
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
        this.promoted = promoted;
        this.createdAt = createdAt;
    }

    /** 작성 시에는 홍보 대상이 아니다. 관리자가 골라서 켠다. */
    public static SiteReview create(Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                                    int score, String content) {
        return new SiteReview(null, contractId, projectId, writerAccountId, writerRole, score, content,
                false, LocalDateTime.now());
    }

    public static SiteReview reconstitute(Long id, Long contractId, Long projectId, Long writerAccountId,
                                          PartyRole writerRole, int score, String content, boolean promoted,
                                          LocalDateTime createdAt) {
        return new SiteReview(id, contractId, projectId, writerAccountId, writerRole, score, content,
                promoted, createdAt);
    }
}
