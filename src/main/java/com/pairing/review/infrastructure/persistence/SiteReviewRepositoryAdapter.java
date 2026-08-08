package com.pairing.review.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.model.SiteReviewVisibility;
import com.pairing.review.domain.repository.SiteReviewRepository;
import com.pairing.review.infrastructure.mapper.SiteReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SiteReviewRepositoryAdapter implements SiteReviewRepository {

    private final SpringDataSiteReviewRepository springDataRepository;
    private final SiteReviewMapper siteReviewMapper;

    @Override
    public SiteReview save(SiteReview siteReview) {
        return siteReviewMapper.toDomain(springDataRepository.save(siteReviewMapper.toJpaEntity(siteReview)));
    }

    @Override
    public Optional<SiteReview> findById(Long id) {
        return springDataRepository.findById(id).map(siteReviewMapper::toDomain);
    }

    @Override
    public Page<SiteReview> search(Integer score, PartyRole writerRole, SiteReviewVisibility visibility,
                                   Boolean promoted, Pageable pageable) {
        return springDataRepository.search(score, writerRole, visibility, promoted, pageable)
                .map(siteReviewMapper::toDomain);
    }

    @Override
    public long count() {
        return springDataRepository.count();
    }

    @Override
    public long countByCreatedAtAfter(LocalDateTime from) {
        return springDataRepository.countByCreatedAtAfter(from);
    }

    @Override
    public long countByPromotedTrue() {
        return springDataRepository.countByPromotedTrue();
    }

    @Override
    public long countByVisibility(SiteReviewVisibility visibility) {
        return springDataRepository.countByVisibility(visibility);
    }

    @Override
    public Double findAverageScore() {
        return springDataRepository.findAverageScore();
    }

    @Override
    public List<Object[]> countGroupByScore() {
        return springDataRepository.countGroupByScore();
    }
}
