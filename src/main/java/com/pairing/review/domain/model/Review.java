package com.pairing.review.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.exception.ReviewErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 계약 상대에 대한 상호 평가 1건. (요구사항 R22)
 *
 * <p>작성 후 수정·삭제할 수 없어 도메인에 변경 메서드가 없다. 계약당 리뷰어 1명은 1건만
 * 쓸 수 있으며, 중복 여부는 {@code ReviewRepository} 조회로 서비스 계층이 확인한다.
 *
 * <p>{@code contractId}/{@code projectId}/{@code revieweeAccountId} 는 원래 계약(Contract) 도메인에서
 * 유도해야 하지만, 그 도메인이 아직 스켈레톤이라 당분간 요청에서 직접 받는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    private Long id;
    private Long contractId;
    private Long projectId;
    private Long reviewerAccountId;
    private PartyRole reviewerRole;
    private Long revieweeAccountId;
    private PartyRole revieweeRole;
    private int score;
    private String content;
    private LocalDateTime createdAt;

    private Review(Long id, Long contractId, Long projectId, Long reviewerAccountId, PartyRole reviewerRole,
                   Long revieweeAccountId, PartyRole revieweeRole, int score, String content,
                   LocalDateTime createdAt) {
        validate(contractId, projectId, reviewerAccountId, revieweeAccountId, score);
        this.id = id;
        this.contractId = contractId;
        this.projectId = projectId;
        this.reviewerAccountId = reviewerAccountId;
        this.reviewerRole = reviewerRole;
        this.revieweeAccountId = revieweeAccountId;
        this.revieweeRole = revieweeRole;
        this.score = score;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Review create(Long contractId, Long projectId, Long reviewerAccountId, PartyRole reviewerRole,
                                Long revieweeAccountId, PartyRole revieweeRole, int score, String content) {
        return new Review(null, contractId, projectId, reviewerAccountId, reviewerRole, revieweeAccountId,
                revieweeRole, score, content, LocalDateTime.now());
    }

    public static Review reconstitute(Long id, Long contractId, Long projectId, Long reviewerAccountId,
                                      PartyRole reviewerRole, Long revieweeAccountId, PartyRole revieweeRole,
                                      int score, String content, LocalDateTime createdAt) {
        return new Review(id, contractId, projectId, reviewerAccountId, reviewerRole, revieweeAccountId,
                revieweeRole, score, content, createdAt);
    }

    private static void validate(Long contractId, Long projectId, Long reviewerAccountId, Long revieweeAccountId,
                                 int score) {
        if (contractId == null || projectId == null || reviewerAccountId == null || revieweeAccountId == null) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        if (reviewerAccountId.equals(revieweeAccountId)) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        if (score < 1 || score > 5) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
    }
}
