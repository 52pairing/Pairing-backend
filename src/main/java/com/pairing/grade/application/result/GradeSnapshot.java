package com.pairing.grade.application.result;

import java.time.LocalDateTime;

/**
 * 등급 판정에 쓰는 실적 한 묶음.
 *
 * <p>승급 안내 문구(마이페이지)와 월간 산정(배치)이 <b>같은 값을 보고 판단해야 한다.</b>
 * 각자 계산하면 "조건을 충족했습니다"라고 안내한 다음 달에 승급이 안 되는 일이 생긴다.
 *
 * @param ratingAverage   받은 리뷰 평균. 리뷰가 없으면 null
 * @param completedCount  대금 지급까지 끝난 계약 건수 (누적)
 * @param lastCompletedAt 가장 최근 완료 시각. 없으면 null. 등급 유지 기준 판정에 쓴다
 */
public record GradeSnapshot(
        Double ratingAverage,
        int completedCount,
        LocalDateTime lastCompletedAt
) {
}
