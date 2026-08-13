package com.pairing.matching.presentation.api.response.admin;

import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "매칭 상태 디버깅 결과")
public record MatchingDiagnosticsResponse(
        ProjectInfo project,
        PositionInfo position,
        SnapshotInfo snapshots,
        EmbeddingInfo embeddings,
        RoundInfo round,
        CountInfo counts,
        LastAiLogInfo lastAiLog
) {

    public static MatchingDiagnosticsResponse from(MatchingDiagnostics diagnostics) {
        return new MatchingDiagnosticsResponse(
                ProjectInfo.from(diagnostics.project()),
                PositionInfo.from(diagnostics.position()),
                SnapshotInfo.from(diagnostics.snapshots()),
                EmbeddingInfo.from(diagnostics.embeddings()),
                RoundInfo.from(diagnostics.round()),
                CountInfo.from(diagnostics.counts()),
                LastAiLogInfo.from(diagnostics.lastAiLog())
        );
    }

    public record ProjectInfo(Long projectId, String title, String status, String paymentStatus) {
        static ProjectInfo from(MatchingDiagnostics.ProjectInfo value) {
            return value == null ? null : new ProjectInfo(value.projectId(), value.title(), value.status(),
                    value.paymentStatus());
        }
    }

    public record PositionInfo(Long positionId, String status, String jobCategory, String jobRole) {
        static PositionInfo from(MatchingDiagnostics.PositionInfo value) {
            return value == null ? null : new PositionInfo(value.positionId(), value.status(), value.jobCategory(),
                    value.jobRole());
        }
    }

    public record SnapshotInfo(boolean projectSnapshotExists, boolean positionSnapshotExists) {
        static SnapshotInfo from(MatchingDiagnostics.SnapshotInfo value) {
            return new SnapshotInfo(value.projectSnapshotExists(), value.positionSnapshotExists());
        }
    }

    public record EmbeddingInfo(boolean positionEmbeddingExists, String positionModel,
                                long freelancerEmbeddingCount) {
        static EmbeddingInfo from(MatchingDiagnostics.EmbeddingInfo value) {
            return new EmbeddingInfo(value.positionEmbeddingExists(), value.positionModel(),
                    value.freelancerEmbeddingCount());
        }
    }

    public record RoundInfo(Long roundId, Integer roundNo, String roundType, String status) {
        static RoundInfo from(MatchingDiagnostics.RoundInfo value) {
            return value == null ? null : new RoundInfo(value.roundId(), value.roundNo(), value.roundType(),
                    value.status());
        }
    }

    public record CountInfo(long candidateCount, long exposedCandidateCount, long requestCount) {
        static CountInfo from(MatchingDiagnostics.CountInfo value) {
            return new CountInfo(value.candidateCount(), value.exposedCandidateCount(), value.requestCount());
        }
    }

    public record LastAiLogInfo(String status, LocalDateTime createdAt, String errorMessage) {
        static LastAiLogInfo from(MatchingDiagnostics.LastAiLogInfo value) {
            return value == null ? null : new LastAiLogInfo(value.status(), value.createdAt(), value.errorMessage());
        }
    }
}
