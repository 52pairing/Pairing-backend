package com.pairing.review.application.service;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.review.application.result.SiteReviewResult;
import com.pairing.review.application.result.SiteReviewSummaryResult;
import com.pairing.review.application.usecase.SiteReviewAdminUseCase;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.model.SiteReviewVisibility;
import com.pairing.review.domain.repository.SiteReviewRepository;
import com.pairing.review.exception.ReviewErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code search}/{@code updateVisibility} 는 일부러 {@code @Transactional} 로 묶지 않는다.
 * {@code toResult()} 가 부르는 {@code ProjectQueryUseCase.getById()} 가 대상 없으면 예외를 던지는데,
 * 같은 트랜잭션 안에서 그 예외를 잡아도 트랜잭션은 이미 rollback-only가 되어 커밋 시점에
 * {@code UnexpectedRollbackException} 이 난다. (ReviewService 와 같은 이유)
 */
@Service
@RequiredArgsConstructor
public class SiteReviewAdminService implements SiteReviewAdminUseCase {

    private final SiteReviewRepository siteReviewRepository;
    private final ProjectQueryUseCase projectQueryUseCase;

    @Override
    @Transactional(readOnly = true)
    public SiteReviewSummaryResult getSummary() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        Double average = siteReviewRepository.findAverageScore();
        long totalCount = siteReviewRepository.count();
        long promotedCount = siteReviewRepository.countByPromotedTrue();

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int score = 5; score >= 1; score--) {
            distribution.put(score, 0L);
        }
        for (Object[] row : siteReviewRepository.countGroupByScore()) {
            distribution.put((Integer) row[0], (Long) row[1]);
        }

        return new SiteReviewSummaryResult(
                average == null ? 0.0 : average,
                totalCount,
                siteReviewRepository.countByCreatedAtAfter(monthStart),
                promotedCount,
                totalCount - promotedCount,
                siteReviewRepository.countByVisibility(SiteReviewVisibility.PUBLIC),
                distribution
        );
    }

    @Override
    public Page<SiteReviewResult> search(Integer score, PartyRole writerRole, SiteReviewVisibility visibility,
                                         Boolean promoted, Pageable pageable) {
        return siteReviewRepository.search(score, writerRole, visibility, promoted, pageable).map(this::toResult);
    }

    @Override
    public SiteReviewResult updateVisibility(Long siteReviewId, SiteReviewVisibility visibility, boolean promoted) {
        SiteReview siteReview = siteReviewRepository.findById(siteReviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.SITE_REVIEW_NOT_FOUND));
        siteReview.updateVisibility(visibility, promoted);
        return toResult(siteReviewRepository.save(siteReview));
    }

    private SiteReviewResult toResult(SiteReview siteReview) {
        String projectTitle;
        try {
            projectTitle = projectQueryUseCase.getById(siteReview.getProjectId()).getTitle();
        } catch (BusinessException e) {
            projectTitle = null;
        }

        return new SiteReviewResult(
                siteReview.getId(),
                siteReview.getWriterRole(),
                maskedWriterName(siteReview),
                siteReview.getScore(),
                siteReview.getContent(),
                projectTitle,
                siteReview.getVisibility(),
                siteReview.isPromoted(),
                siteReview.getCreatedAt()
        );
    }

    private String maskedWriterName(SiteReview siteReview) {
        // TODO: 계정 이름/회사명을 조회해 마스킹(예: "고**") 처리. 지금은 작성자 구분만 노출한다.
        return siteReview.getWriterRole() == PartyRole.CLIENT ? "클라이언트" : "프리랜서";
    }
}
