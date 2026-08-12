package com.pairing.review.domain.repository;

import com.pairing.review.domain.model.SiteReview;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SiteReviewRepository {

    SiteReview save(SiteReview siteReview);

    Optional<SiteReview> findById(Long id);

    /** [비로그인 메인] 공개 + 홍보 활용 + 별점 하한 이상, 최신순. */
    List<SiteReview> findPromoted(int minScore, Pageable pageable);
}
