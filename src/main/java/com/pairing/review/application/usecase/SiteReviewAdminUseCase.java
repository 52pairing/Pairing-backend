package com.pairing.review.application.usecase;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.application.result.SiteReviewResult;
import com.pairing.review.application.result.SiteReviewSummaryResult;
import com.pairing.review.domain.model.SiteReviewVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SiteReviewAdminUseCase {

    SiteReviewSummaryResult getSummary();

    Page<SiteReviewResult> search(Integer score, PartyRole writerRole, SiteReviewVisibility visibility,
                                  Boolean promoted, Pageable pageable);

    /** 없으면 {@code RV_003}. */
    SiteReviewResult updateVisibility(Long siteReviewId, SiteReviewVisibility visibility, boolean promoted);
}
