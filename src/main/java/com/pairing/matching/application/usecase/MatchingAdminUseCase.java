package com.pairing.matching.application.usecase;

import com.pairing.matching.application.result.admin.AiLogItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface MatchingAdminUseCase {

    EmbeddingMissingResult findMissingEmbeddings(String targetType, Pageable pageable);

    void reindexFreelancer(Long freelancerId);

    void reindexPosition(Long projectId, Long positionId);

    Page<AiLogItem> findAiLogs(String agentType, String refType, Long refId, String status,
                               LocalDateTime from, LocalDateTime to, Pageable pageable);

    MatchingDiagnostics findDiagnostics(Long projectId, Long positionId);
}
