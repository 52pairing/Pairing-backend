package com.pairing.review.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SpringDataReviewRepository extends JpaRepository<ReviewJpaEntity, Long> {

    boolean existsByContractIdAndReviewerAccountId(Long contractId, Long reviewerAccountId);

    Page<ReviewJpaEntity> findByRevieweeAccountId(Long accountId, Pageable pageable);

    Page<ReviewJpaEntity> findByReviewerAccountId(Long accountId, Pageable pageable);

    long countByRevieweeAccountId(Long accountId);

    @Query("SELECT AVG(r.score) FROM ReviewJpaEntity r WHERE r.revieweeAccountId = :accountId")
    Double findAverageScoreByRevieweeAccountId(@Param("accountId") Long accountId);

    /** 평균과 건수를 한 번에 집계한다. 리뷰가 없는 계정은 GROUP BY 특성상 결과에 나오지 않는다. */
    @Query("""
            SELECT r.revieweeAccountId AS accountId,
                   AVG(r.score) AS averageScore,
                   COUNT(r) AS reviewCount
              FROM ReviewJpaEntity r
             WHERE r.revieweeAccountId IN :accountIds
             GROUP BY r.revieweeAccountId
            """)
    List<RatingProjection> findRatingsByRevieweeAccountIds(@Param("accountIds") Collection<Long> accountIds);

    /** 집계 결과를 받는 투영. 엔티티를 통째로 읽지 않으려고 인터페이스로 둔다. */
    interface RatingProjection {
        Long getAccountId();

        Double getAverageScore();

        long getReviewCount();
    }
}
