package com.pairing.grade.application.result;

/**
 * {@code completedProjectCount} 는 contract 도메인이 아직 없어 항상 0이다(TODO).
 * {@code nextGradeGuide} 도 그래서 완료 건수 조건까지는 정확히 안내하지 못하고, 별점 조건 충족 여부만 알려준다.
 */
public record MyGradeResult(
        String grade,
        String label,
        int completedProjectCount,
        Double ratingAverage,
        String nextGrade,
        String nextGradeGuide,
        String checkedGuide
) {
}
