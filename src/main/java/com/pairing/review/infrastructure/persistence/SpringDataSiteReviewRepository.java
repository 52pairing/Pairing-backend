package com.pairing.review.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReviewVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataSiteReviewRepository extends JpaRepository<SiteReviewJpaEntity, Long> {

    long countByCreatedAtAfter(LocalDateTime from);

    long countByPromotedTrue();

    long countByVisibility(SiteReviewVisibility visibility);

    @Query("SELECT AVG(s.score) FROM SiteReviewJpaEntity s")
    Double findAverageScore();

    @Query("SELECT s.score, COUNT(s) FROM SiteReviewJpaEntity s GROUP BY s.score")
    List<Object[]> countGroupByScore();

    @Query("SELECT s FROM SiteReviewJpaEntity s "
            + "WHERE (:score IS NULL OR s.score = :score) "
            + "AND (:writerRole IS NULL OR s.writerRole = :writerRole) "
            + "AND (:visibility IS NULL OR s.visibility = :visibility) "
            + "AND (:promoted IS NULL OR s.promoted = :promoted)")
    Page<SiteReviewJpaEntity> search(@Param("score") Integer score, @Param("writerRole") PartyRole writerRole,
                                     @Param("visibility") SiteReviewVisibility visibility,
                                     @Param("promoted") Boolean promoted, Pageable pageable);
}
