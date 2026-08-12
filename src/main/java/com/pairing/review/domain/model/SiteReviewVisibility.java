package com.pairing.review.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사이트 리뷰 공개 상태. (요구사항 R40)
 *
 * <p><b>기본값은 공개다.</b> 관리자가 사후에 부적절한 후기만 비공개로 내린다. 사전 검수 방식은
 * 관리자가 손대기 전까지 후기가 하나도 안 보여서, 후기 수가 늘수록 밀린다.
 *
 * <p>메인 노출은 이 값과 별개다. 홍보 활용까지 켜야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum SiteReviewVisibility {

    PRIVATE("비공개"),
    PUBLIC("공개");

    private final String label;
}
