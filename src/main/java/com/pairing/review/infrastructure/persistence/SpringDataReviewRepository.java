package com.pairing.review.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataReviewRepository extends JpaRepository<ReviewJpaEntity, Long> {

    boolean existsByContractIdAndReviewerAccountId(Long contractId, Long reviewerAccountId);

    Page<ReviewJpaEntity> findByRevieweeAccountId(Long accountId, Pageable pageable);

    Page<ReviewJpaEntity> findByReviewerAccountId(Long accountId, Pageable pageable);

    long countByRevieweeAccountId(Long accountId);

    @Query("SELECT AVG(r.score) FROM ReviewJpaEntity r WHERE r.revieweeAccountId = :accountId")
    Double findAverageScoreByRevieweeAccountId(@Param("accountId") Long accountId);
}
