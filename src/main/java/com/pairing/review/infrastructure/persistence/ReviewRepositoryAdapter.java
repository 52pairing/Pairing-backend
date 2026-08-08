package com.pairing.review.infrastructure.persistence;

import com.pairing.review.domain.model.Review;
import com.pairing.review.domain.repository.ReviewRepository;
import com.pairing.review.infrastructure.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryAdapter implements ReviewRepository {

    private final SpringDataReviewRepository springDataRepository;
    private final ReviewMapper reviewMapper;

    @Override
    public Review save(Review review) {
        return reviewMapper.toDomain(springDataRepository.save(reviewMapper.toJpaEntity(review)));
    }

    @Override
    public boolean existsByContractIdAndReviewerAccountId(Long contractId, Long reviewerAccountId) {
        return springDataRepository.existsByContractIdAndReviewerAccountId(contractId, reviewerAccountId);
    }

    @Override
    public Page<Review> findByRevieweeAccountId(Long accountId, Pageable pageable) {
        return springDataRepository.findByRevieweeAccountId(accountId, pageable).map(reviewMapper::toDomain);
    }

    @Override
    public Page<Review> findByReviewerAccountId(Long accountId, Pageable pageable) {
        return springDataRepository.findByReviewerAccountId(accountId, pageable).map(reviewMapper::toDomain);
    }

    @Override
    public long countByRevieweeAccountId(Long accountId) {
        return springDataRepository.countByRevieweeAccountId(accountId);
    }

    @Override
    public Double findAverageScoreByRevieweeAccountId(Long accountId) {
        return springDataRepository.findAverageScoreByRevieweeAccountId(accountId);
    }
}
