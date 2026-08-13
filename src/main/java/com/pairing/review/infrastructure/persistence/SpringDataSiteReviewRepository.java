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
     *
     * <p><b>정렬을 쿼리 안에 둔다.</b> 호출부의 {@code Pageable} 에 맡기면 누군가 정렬 없는
     * {@code Pageable} 을 넘기는 순간 순서 보장이 사라진다. 조회 조건과 정렬은 같이 움직인다.
     *
     * <p><b>{@code id} 를 2차 키로 쓴다.</b> {@code createdAt} 은 {@code LocalDateTime.now()} 로
     * 찍히는데, 후기 여러 건이 시계 분해능보다 짧은 간격으로 저장되면 값이 같아진다. 그러면
     * {@code createdAt} 만으로는 순서가 정해지지 않아 DB 가 돌려주는 순서를 그대로 따르고,
     * {@code limit} 으로 잘라 내려주므로 <b>새로고침할 때마 다른 후기가 보인다.</b>
     * {@code id} 는 유일해서 순서가 항상 확정된다.
     */
    @Query("SELECT s FROM SiteReviewJpaEntity s "
            + "WHERE s.promoted = true AND s.score >= :minScore "
            + "ORDER BY s.createdAt DESC, s.id DESC")
    List<SiteReviewJpaEntity> findPromoted(@Param("minScore") int minScore, Pageable pageable);
}
