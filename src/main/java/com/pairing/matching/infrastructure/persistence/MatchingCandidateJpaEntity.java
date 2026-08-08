package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.CandidateStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * matching_candidate 테이블 매핑.
 *
 * <p>created_at / updated_at 은 매핑하지 않는다. DB 기본값과 트리거가 채운다.
 */
@Entity
@Table(name = "matching_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingCandidateJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private CandidateStage stage;

    @Column(name = "similarity", columnDefinition = "numeric(6,4)")
    private Double similarity;

    @Column(name = "base_score", columnDefinition = "numeric(5,2)")
    private Double baseScore;

    @Column(name = "grade_weight", columnDefinition = "numeric(5,2)")
    private Double gradeWeight;

    @Column(name = "fit_score", columnDefinition = "numeric(5,2)")
    private Double fitScore;

    @Column(name = "guard_passed")
    private Boolean guardPassed;

    @Column(name = "guard_reason", length = 500)
    private String guardReason;

    @Column(name = "fit_reason", columnDefinition = "TEXT")
    private String fitReason;

    @Column(name = "rank_no")
    private Integer rankNo;

    @Column(name = "is_exposed", nullable = false)
    private boolean exposed;

    @Column(name = "is_rejected", nullable = false)
    private boolean rejected;

    public MatchingCandidateJpaEntity(Long id, Long roundId, Long positionId, Long freelancerId,
                                      CandidateStage stage, Double similarity, Double baseScore, Double gradeWeight,
                                      Double fitScore, Boolean guardPassed, String guardReason, String fitReason,
                                      Integer rankNo, boolean exposed, boolean rejected) {
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
}
