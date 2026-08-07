package com.pairing.negotiation.application.command;

import com.pairing.negotiation.domain.model.FreelancerConditionSnapshot;

/**
 * 매칭 수락 시 협상 생성 커맨드. 매칭 도메인이 동기로 넘겨준다.
 *
 * <ul>
 *   <li>{@code freelancerId} = freelancer_profile.id (account.id 아님)</li>
 *   <li>{@code budgetCap} = 이 프리에게 배분된 순예산 월단가(원). 매칭 계산값을 그대로 받는다(재계산 X).</li>
 *   <li>{@code snapshot} = 매칭 시점 캡처된 프리 조건. 클라 희망값(project)은 협상이 직접 조회해 diff.</li>
 * </ul>
 */
public record CreateNegotiationCommand(
        Long requestId,
        Long projectId,
        Long positionId,
        Long freelancerId,
        Long budgetCap,
        FreelancerConditionSnapshot snapshot
) {
}
