package com.pairing.review.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사이트 리뷰 공개 상태. (요구사항 R40)
 *
 * <p>기본값은 비공개다. 관리자가 확인한 뒤 공개로 바꾸고, 홍보 활용 여부는 따로 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum SiteReviewVisibility {

    PRIVATE("비공개"),
    PUBLIC("공개");

    private final String label;
}
