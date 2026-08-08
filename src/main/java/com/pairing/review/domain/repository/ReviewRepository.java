package com.pairing.review.domain.repository;

import com.pairing.review.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepository {

    Review save(Review review);

    boolean existsByContractIdAndReviewerAccountId(Long contractId, Long reviewerAccountId);

    Page<Review> findByRevieweeAccountId(Long accountId, Pageable pageable);

    Page<Review> findByReviewerAccountId(Long accountId, Pageable pageable);

    long countByRevieweeAccountId(Long accountId);

    /** 받은 리뷰가 없으면 null. */
    Double findAverageScoreByRevieweeAccountId(Long accountId);
}
