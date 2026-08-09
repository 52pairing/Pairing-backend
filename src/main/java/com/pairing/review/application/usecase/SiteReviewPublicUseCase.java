package com.pairing.review.application.usecase;

import com.pairing.review.application.result.SiteReviewResult;

import java.util.List;

/** 비로그인 메인 페이지가 쓰는 홍보 리뷰 조회. 관리자 전용이 아니라 별도 인터페이스로 둔다. */
public interface SiteReviewPublicUseCase {

    /** 공개 + 홍보 활용으로 설정되고 별점 4점 이상인 리뷰만, 최신순으로 최대 {@code limit} 건. */
    List<SiteReviewResult> findPromoted(int limit);
}
