package com.pairing.matching.domain.model;

import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 후보 1건 (한 회차 안의 프리랜서 1명).
 *
 * <p>단계를 따라 진행한다: EMBEDDING(1차 추림, 유사도만 있음)
 * -&gt; LLM_FINAL(LLM이 baseScore·fitReason 산출) -&gt; GUARD(가드 검증 완료, fitScore 확정).
 * 되돌아가지 않는다.
 *
 * <p>점수는 0~100 스케일이다. fitScore = baseScore에 클라이언트 등급 가중치(gradeWeight, %)를
 * 반영한 최종값이며, 클라이언트 화면에는 숫자 자체를 노출하지 않고 fitReason(태그)만 보여준다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingCandidate {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 100.0;

    private Long id;
    private Long roundId;
    private Long positionId;
    private Long freelancerId;
    private CandidateStage stage;
    private Double similarity;
    private Double baseScore;
    private Double gradeWeight;
    private Double fitScore;
    private Boolean guardPassed;
    private String guardReason;
    private String fitReason;
    private Integer rankNo;
    private boolean exposed;
    private boolean rejected;

    private MatchingCandidate(Long id, Long roundId, Long positionId, Long freelancerId, CandidateStage stage,
                              Double similarity, Double baseScore, Double gradeWeight, Double fitScore,
                              Boolean guardPassed, String guardReason, String fitReason, Integer rankNo,
                              boolean exposed, boolean rejected) {
        this.id = id;
        this.roundId = roundId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.stage = stage;
        this.similarity = similarity;
        this.baseScore = baseScore;
        this.gradeWeight = gradeWeight;
        this.fitScore = fitScore;
        this.guardPassed = guardPassed;
        this.guardReason = guardReason;
        this.fitReason = fitReason;
        this.rankNo = rankNo;
        this.exposed = exposed;
        this.rejected = rejected;
    }

    /** Stage C(임베딩 유사도) 결과로 후보를 만든다. similarity는 이후 단계에서 참고용으로만 남고 점수엔 안 쓴다. */
    public static MatchingCandidate createFromEmbedding(Long roundId, Long positionId, Long freelancerId,
                                                         double similarity) {
        if (roundId == null || positionId == null || freelancerId == null) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        return new MatchingCandidate(null, roundId, positionId, freelancerId, CandidateStage.EMBEDDING,
                similarity, null, null, null, null, null, null, null, false, false);
    }

    public static MatchingCandidate reconstitute(Long id, Long roundId, Long positionId, Long freelancerId,
                                                 CandidateStage stage, Double similarity, Double baseScore,
                                                 Double gradeWeight, Double fitScore, Boolean guardPassed,
                                                 String guardReason, String fitReason, Integer rankNo,
                                                 boolean exposed, boolean rejected) {
        return new MatchingCandidate(id, roundId, positionId, freelancerId, stage, similarity, baseScore,
                gradeWeight, fitScore, guardPassed, guardReason, fitReason, rankNo, exposed, rejected);
    }

    /** Stage E: LLM이 산출한 원점수·근거를 반영한다(등급 가중치 반영 전). */
    public void applyLlmResult(double baseScore, String fitReason) {
        if (this.stage != CandidateStage.EMBEDDING) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        this.baseScore = clamp(baseScore);
        this.fitReason = fitReason;
        this.stage = CandidateStage.LLM_FINAL;
    }

    /** 클라이언트 등급 가중치(%)를 반영해 최종 fitScore를 확정한다. 등급은 노출 수가 아니라 우선순위에만 영향을 준다. */
    public void applyGradeWeight(double gradeWeightPercent) {
        if (this.stage != CandidateStage.LLM_FINAL || this.baseScore == null) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        this.gradeWeight = gradeWeightPercent;
        this.fitScore = clamp(this.baseScore * (1 + gradeWeightPercent / 100));
    }

    /** Stage F: 가드 AI(규칙 기반) 검증 결과를 반영한다. */
    public void applyGuard(boolean passed, String reason) {
        this.guardPassed = passed;
        this.guardReason = reason;
        this.stage = CandidateStage.GUARD;
    }

    /** 클라이언트 화면에 노출한다. 노출 수는 모집 인원(포지션 headcount)을 넘지 않아야 한다(호출 측 책임). */
    public void expose(int rankNo) {
        this.rankNo = rankNo;
        this.exposed = true;
    }

    /**
     * 클라이언트가 이 후보를 거절(비활성 표시)한다. 요청을 보낸 적 없어도 가능하다.
     *
     * <p>이 자체는 무료 재추천 조건과 무관하다(P41: 무료 재추천은 "발송한 요청이 전원 거절·만료"됐을 때만 적용).
     * 거절해도 이 프리랜서는 같은 포지션의 다음 회차에 다시 후보로 나오지 않는다
     * (MatchingCandidateRepository.findFreelancerIdsByPositionId 가 회차 불문 전체 제외 대상으로 이미 처리).
     */
    public void reject() {
        this.rejected = true;
    }

    /** 품질 경고 판단 기준(P09). 클라이언트 화면에는 이 값 자체가 아니라 임계값 이하 여부만 노출한다. */
    public boolean isBelowQualityThreshold(double threshold) {
        return this.fitScore != null && this.fitScore < threshold;
    }

    private double clamp(double score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }
}
