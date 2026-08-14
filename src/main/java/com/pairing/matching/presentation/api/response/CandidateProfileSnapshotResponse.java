package com.pairing.matching.presentation.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.freelancer.presentation.api.response.ResumeResponse;
import com.pairing.matching.application.result.FreelancerCardSummary;

import java.time.LocalDateTime;
import java.util.List;

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
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SnapshotPayload(
            FreelancerCardSummary card,
            FreelancerConditionResponse condition,
            ResumeResponse resume,
            LocalDateTime capturedAt
    ) {
    }
}
