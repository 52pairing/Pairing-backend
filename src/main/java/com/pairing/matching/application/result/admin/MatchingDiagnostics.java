package com.pairing.matching.application.result.admin;

import java.time.LocalDateTime;

public record MatchingDiagnostics(
        ProjectInfo project,
        PositionInfo position,
        SnapshotInfo snapshots,
        EmbeddingInfo embeddings,
        RoundInfo round,
        CountInfo counts,
        LastAiLogInfo lastAiLog
) {

    public record ProjectInfo(Long projectId, String title, String status, String paymentStatus) {
    }

    public record PositionInfo(Long positionId, String status, String jobCategory, String jobRole) {
    }

    public record SnapshotInfo(boolean projectSnapshotExists, boolean positionSnapshotExists) {
    }

    public record EmbeddingInfo(boolean positionEmbeddingExists, String positionModel,
                                long freelancerEmbeddingCount) {
    }

    public record RoundInfo(Long roundId, Integer roundNo, String roundType, String status) {
    }

    public record CountInfo(long candidateCount, long exposedCandidateCount, long requestCount) {
    }

    public record LastAiLogInfo(String status, LocalDateTime createdAt, String errorMessage) {
    }
}
