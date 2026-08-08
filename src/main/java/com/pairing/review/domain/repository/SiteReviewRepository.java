package com.pairing.review.domain.repository;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.model.SiteReviewVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SiteReviewRepository {

    SiteReview save(SiteReview siteReview);

    Optional<SiteReview> findById(Long id);

    Page<SiteReview> search(Integer score, PartyRole writerRole, SiteReviewVisibility visibility, Boolean promoted,
                            Pageable pageable);

    long count();

    long countByCreatedAtAfter(LocalDateTime from);

    long countByPromotedTrue();

    long countByVisibility(SiteReviewVisibility visibility);

    /** 리뷰가 없으면 null. */
    Double findAverageScore();

    /** 별점(1~5)별 건수. 값이 0인 별점도 항상 포함한다. */
    List<Object[]> countGroupByScore();
}
