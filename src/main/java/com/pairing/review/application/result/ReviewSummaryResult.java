package com.pairing.review.application.result;

/** {@code grade} 는 review 집계가 아니라 계정에 이미 저장된 현재 등급값을 그대로 보여준다. */
public record ReviewSummaryResult(
        Double averageScore,
        int reviewCount,
        String grade
) {
}
