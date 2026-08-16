package com.pairing.review.domain.model;

/**
 * 한 사람이 받은 평점 집계. 평균과 건수만 담는다.
 *
 * <p>{@code ReviewSummaryResult} 와 달리 <b>등급이 없다.</b> 등급은 계정·프로필을 더 읽어야 나오는데,
 * 목록 화면은 대개 프로필을 이미 손에 들고 있어서 그 조회가 통째로 낭비가 된다. 평점만 필요한 쪽이 이걸 쓴다.
 */
public record ReviewRating(
        Long accountId,
        /** 받은 리뷰가 없으면 null. */
        Double averageScore,
        int reviewCount
) {

    /** 받은 리뷰가 한 건도 없는 계정. 집계 쿼리는 이런 계정을 아예 돌려주지 않아 부르는 쪽이 채운다. */
    public static ReviewRating empty(Long accountId) {
        return new ReviewRating(accountId, null, 0);
    }
}
