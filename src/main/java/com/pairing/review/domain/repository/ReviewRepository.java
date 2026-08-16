package com.pairing.review.domain.repository;

import com.pairing.review.domain.model.Review;
import com.pairing.review.domain.model.ReviewRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository {

    Review save(Review review);

    boolean existsByContractIdAndReviewerAccountId(Long contractId, Long reviewerAccountId);

    Page<Review> findByRevieweeAccountId(Long accountId, Pageable pageable);

    Page<Review> findByReviewerAccountId(Long accountId, Pageable pageable);

    long countByRevieweeAccountId(Long accountId);

    /** 받은 리뷰가 없으면 null. */
    Double findAverageScoreByRevieweeAccountId(Long accountId);

    /**
     * 여러 계정의 평균·건수를 집계 쿼리 한 번으로 가져온다.
     *
     * <p>목록 화면이 사람마다 되물으면 N+1 이다. <b>받은 리뷰가 없는 계정은 결과에 없다</b> —
     * GROUP BY 는 행이 없는 그룹을 만들지 않는다. 부르는 쪽이 {@code ReviewRating.empty} 로 채운다.
     */
    List<ReviewRating> findRatingsByRevieweeAccountIds(Collection<Long> accountIds);
}
