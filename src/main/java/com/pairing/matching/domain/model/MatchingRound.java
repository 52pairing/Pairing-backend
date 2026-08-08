package com.pairing.matching.domain.model;

import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 회차. 포지션 하나에 대한 추천 실행 1건(최초 추천 또는 재추천)을 나타낸다.
 *
 * <p>1차 후보 풀(embeddingPoolSize)은 모집 인원(exposeCount) x 3이다(R01).
 * lowScoreWarned는 이 회차의 대기 순번(노출 안 된 후보) 중 품질 경고 임계값 미달이
 * 하나라도 있으면 true가 된다 — 다음 회차로 넘어가기 전에 미리 보여주는 용도다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRound {

    private Long id;
    private Long projectId;
    private Long positionId;
    private int roundNo;
    private RecommendationType roundType;
    private Integer requestedCount;
    private long costAmount;
    private int exposeCount;
    private Integer embeddingPoolSize;
    private boolean lowScoreWarned;
    private MatchingRoundStatus status;

    private MatchingRound(Long id, Long projectId, Long positionId, int roundNo, RecommendationType roundType,
                          Integer requestedCount, long costAmount, int exposeCount, Integer embeddingPoolSize,
                          boolean lowScoreWarned, MatchingRoundStatus status) {
        this.id = id;
        this.projectId = projectId;
        this.positionId = positionId;
        this.roundNo = roundNo;
        this.roundType = roundType;
        this.requestedCount = requestedCount;
        this.costAmount = costAmount;
        this.exposeCount = exposeCount;
        this.embeddingPoolSize = embeddingPoolSize;
        this.lowScoreWarned = lowScoreWarned;
        this.status = status;
    }

    /** 새 회차를 만든다. embeddingPoolSize는 호출 측이 "노출 인원 x 3" 등으로 계산해서 넘긴다. */
    public static MatchingRound create(Long projectId, Long positionId, int roundNo, RecommendationType roundType,
                                       Integer requestedCount, long costAmount, int exposeCount,
                                       Integer embeddingPoolSize) {
        if (projectId == null || positionId == null || roundType == null || exposeCount <= 0) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        return new MatchingRound(null, projectId, positionId, roundNo, roundType, requestedCount, costAmount,
                exposeCount, embeddingPoolSize, false, MatchingRoundStatus.RUNNING);
    }

    public static MatchingRound reconstitute(Long id, Long projectId, Long positionId, int roundNo,
                                             RecommendationType roundType, Integer requestedCount, long costAmount,
                                             int exposeCount, Integer embeddingPoolSize, boolean lowScoreWarned,
                                             MatchingRoundStatus status) {
        return new MatchingRound(id, projectId, positionId, roundNo, roundType, requestedCount, costAmount,
                exposeCount, embeddingPoolSize, lowScoreWarned, status);
    }

    /** 대기 순번(노출 안 된 후보)에 품질 경고 대상이 있음을 표시한다. 최초 추천화면에서 미리 보여주는 배너 용도(P09). */
    public void warnLowScore() {
        this.lowScoreWarned = true;
    }

    public void complete() {
        assertRunning();
        this.status = MatchingRoundStatus.COMPLETED;
    }

    public void fail() {
        assertRunning();
        this.status = MatchingRoundStatus.FAILED;
    }

    /** 조건에 맞는 후보가 하나도 없어 이 회차가 후보 없이 끝났음을 표시한다. */
    public void exhaust() {
        assertRunning();
        this.status = MatchingRoundStatus.EXHAUSTED;
    }

    private void assertRunning() {
        if (this.status != MatchingRoundStatus.RUNNING) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }
}
