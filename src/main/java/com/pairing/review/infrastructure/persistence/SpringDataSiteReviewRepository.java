package com.pairing.review.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataSiteReviewRepository extends JpaRepository<SiteReviewJpaEntity, Long> {

    /**
     * 비로그인 메인에 내려줄 후기.
     *
     * <p>관리자가 홍보로 켠 것 중 {@code minScore} 이상만 가져온다. 홍보 설정은
     * 관리자 서버(pairing-admin)가 하고, 이 서버는 그 결과만 읽는다.
     */
    @Query("SELECT s FROM SiteReviewJpaEntity s "
            + "WHERE s.promoted = true AND s.score >= :minScore")
    List<SiteReviewJpaEntity> findPromoted(@Param("minScore") int minScore, Pageable pageable);
}
