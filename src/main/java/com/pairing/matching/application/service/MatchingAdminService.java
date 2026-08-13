package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.MatchingAdminReaderPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.admin.AiLogItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import com.pairing.matching.application.usecase.MatchingAdminUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingAdminService implements MatchingAdminUseCase {

    private final MatchingAdminReaderPort matchingAdminReaderPort;
    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final MatchingPort matchingPort;

    @Override
    public EmbeddingMissingResult findMissingEmbeddings(String targetType, Pageable pageable) {
        return matchingAdminReaderPort.findMissingEmbeddings(targetType, pageable);
    }

    @Override
    @Async
    public void reindexFreelancer(Long freelancerId) {
        try {
            freelancerEmbeddingRefresher.refreshByFreelancerId(freelancerId);
        } catch (Exception e) {
            log.warn("[관리자 프리랜서 임베딩 재색인 실패] freelancerId={}", freelancerId, e);
        }
    }

    @Override
    @Async
    public void reindexPosition(Long projectId, Long positionId) {
        try {
            var summary = projectDirectoryPort.findPositionSummary(projectId, positionId);
            matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
        } catch (Exception e) {
            log.warn("[관리자 포지션 임베딩 재색인 실패] projectId={}, positionId={}", projectId, positionId, e);
        }
    }

    @Override
    public Page<AiLogItem> findAiLogs(String agentType, String refType, Long refId, String status,
                                      LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return matchingAdminReaderPort.findAiLogs(agentType, refType, refId, status, from, to, pageable);
    }

    @Override
    public MatchingDiagnostics findDiagnostics(Long projectId, Long positionId) {
        return matchingAdminReaderPort.findDiagnostics(projectId, positionId);
    }
}
