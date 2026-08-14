package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
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

import java.time.LocalDateTime;

/**
 * matching_round 테이블 매핑.
 *
 * <p>created_at / updated_at 은 매핑하지 않는다. DB 기본값과 트리거가 채운다(account 도메인과 동일 컨벤션).
 */
@Entity
@Table(name = "matching_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingRoundJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_type", nullable = false, length = 20)
    private RecommendationType roundType;

    @Column(name = "requested_count")
    private Integer requestedCount;

    @Column(name = "cost_amount", nullable = false, columnDefinition = "numeric(15,0)")
    private long costAmount;

    @Column(name = "expose_count", nullable = false)
    private int exposeCount;

    @Column(name = "embedding_pool_size")
    private Integer embeddingPoolSize;

    @Column(name = "low_score_warned", nullable = false)
    private boolean lowScoreWarned;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchingRoundStatus status;

    /**
     * 생성 시각. <b>읽기 전용 매핑이다</b>({@code insertable/updatable = false}) — 값은 DB 기본값
     * {@code CURRENT_TIMESTAMP}가 채운다. 도메인 모델에는 없는 값이고, "언제부터 RUNNING이었나"를
     * 판단하는 조회에만 쓴다({@code findStaleRunning}).
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public MatchingRoundJpaEntity(Long id, Long projectId, Long positionId, int roundNo, RecommendationType roundType,
                                  Integer requestedCount, long costAmount, int exposeCount,
                                  Integer embeddingPoolSize, boolean lowScoreWarned, MatchingRoundStatus status) {
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
}
