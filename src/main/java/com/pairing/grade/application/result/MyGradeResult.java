package com.pairing.grade.application.result;

/**
 * {@code completedProjectCount} 는 대금 지급까지 끝난 계약 건수다. 성공보수 수수료가 결제되어
 * 프로젝트가 종료(CLOSED)된 건만 센다. 리뷰 작성 조건과 같은 기준이다. (P51)
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
