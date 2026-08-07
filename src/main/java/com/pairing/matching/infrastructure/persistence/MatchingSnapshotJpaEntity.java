package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.SnapshotType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * matching_snapshot 테이블 매핑.
 *
 * <p>snapshot_json은 JSONB 컬럼이라 {@code @JdbcTypeCode(SqlTypes.JSON)}으로 원문 문자열째 매핑한다.
 * 불변 데이터라 created_at만 있고 updated_at은 없다(테이블 자체에 컬럼 없음, 매핑도 안 함).
 */
@Entity
@Table(name = "matching_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "freelancer_id")
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 20)
    private SnapshotType snapshotType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    public MatchingSnapshotJpaEntity(Long id, Long projectId, Long positionId, Long freelancerId,
                                     SnapshotType snapshotType, String snapshotJson) {
        this.id = id;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.snapshotType = snapshotType;
        this.snapshotJson = snapshotJson;
    }
}
