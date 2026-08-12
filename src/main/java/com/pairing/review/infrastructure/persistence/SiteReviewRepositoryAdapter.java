package com.pairing.review.infrastructure.persistence;

import com.pairing.review.domain.model.SiteReview;
import com.pairing.review.domain.repository.SiteReviewRepository;
import com.pairing.review.infrastructure.mapper.SiteReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
    public List<SiteReview> findPromoted(int minScore, Pageable pageable) {
        return springDataRepository.findPromoted(minScore, pageable).stream()
                .map(siteReviewMapper::toDomain)
                .toList();
    }

}
