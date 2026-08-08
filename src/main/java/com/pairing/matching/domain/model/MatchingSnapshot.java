package com.pairing.matching.domain.model;

import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 시점 정보 동결본(R17/R21/R30 — "매칭 시점 정보 기준, 수정 전 정보 사용").
 *
 * <p>한 번 만들어지면 바뀌지 않는다(불변). 저장 형태는 JSON 문자열이며, 어떤 값이 들어가는지는
 * snapshotType에 따라 다르다:
 * - PROJECT/POSITION: 클라이언트 쪽 조건(work_style, work_form, budget 등)
 * - FREELANCER: 협상 diff 대상 4개(payUnit+payAmount, workStyle, workForm, availableFrom)
 *   + 보조 2개(minAcceptAmount, startNegotiable). periodValue가 null이면 안 넣는다.
 *
 * <p>project_id는 항상 있고, position_id/freelancer_id는 snapshotType에 따라 null일 수 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingSnapshot {

    private Long id;
    private Long projectId;
    private Long positionId;
    private Long freelancerId;
    private SnapshotType snapshotType;
    private String snapshotJson;

    private MatchingSnapshot(Long id, Long projectId, Long positionId, Long freelancerId,
                             SnapshotType snapshotType, String snapshotJson) {
        this.id = id;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.snapshotType = snapshotType;
        this.snapshotJson = snapshotJson;
    }

    public static MatchingSnapshot create(Long projectId, Long positionId, Long freelancerId,
                                          SnapshotType snapshotType, String snapshotJson) {
        if (projectId == null || snapshotType == null || snapshotJson == null || snapshotJson.isBlank()) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
        return new MatchingSnapshot(null, projectId, positionId, freelancerId, snapshotType, snapshotJson);
    }

    public static MatchingSnapshot reconstitute(Long id, Long projectId, Long positionId, Long freelancerId,
                                                SnapshotType snapshotType, String snapshotJson) {
        return new MatchingSnapshot(id, projectId, positionId, freelancerId, snapshotType, snapshotJson);
    }
}
