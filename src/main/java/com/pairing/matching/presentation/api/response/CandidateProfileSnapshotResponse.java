package com.pairing.matching.presentation.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.freelancer.presentation.api.response.ResumeResponse;
import com.pairing.global.infrastructure.s3.CdnMappable;
import com.pairing.matching.application.result.FreelancerCardSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 후보 상세(스냅샷).
 *
 * <p>{@link CdnMappable} 을 구현해야 {@code profileImageUrl} 이 CDN 절대 URL로 나간다.
 * 카드 목록({@link CandidateResponse})과 같은 이유다 — 빠뜨리면 object key 가 그대로 나간다.
 */
public record CandidateProfileSnapshotResponse(
        Long candidateId,
        Long projectId,
        Long positionId,
        Long freelancerId,
        String name,
        String profileImageUrl,
        String grade,
        Double ratingAverage,
        int reviewCount,
        Double fitScore,
        List<String> fitReasons,
        Integer rankNo,
        boolean requested,
        boolean rejected,
        LocalDateTime capturedAt,
        FreelancerConditionResponse condition,
        ResumeResponse resume
) implements CdnMappable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotPayload(
            FreelancerCardSummary card,
            FreelancerConditionResponse condition,
            ResumeResponse resume,
            LocalDateTime capturedAt
    ) {
    }
}
