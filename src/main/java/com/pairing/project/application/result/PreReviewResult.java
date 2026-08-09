package com.pairing.project.application.result;

import com.pairing.meta.domain.model.JobRole;

import java.util.List;

/**
 * 사전 검수 결과. (정책 P02)
 *
 * <p>{@code items} 는 요청한 포지션과 같은 순서·같은 개수다. 같은 직무를 두 번 넣으면 두 건으로 답한다.
 * 화면이 카드와 1:1로 붙여야 해서 직무 기준으로 병합하지 않는다.
 */
public record PreReviewResult(boolean allMatchable, List<Item> items) {

    public record Item(
            int positionIndex,
            JobRole jobRole,
            int headcount,
            int expectedCandidateCount,
            boolean matchable,
            String message,
            List<String> suggestions
    ) {
    }
}
