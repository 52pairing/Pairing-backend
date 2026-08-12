package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.EmbeddingReindexResult;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 임베딩 일괄 재색인. 실제 Gemini/DB 없이 포트만 목으로 대체해 검증한다 — 이 서비스는 조립 로직을
 * 재사용해 호출만 반복하는 얇은 오케스트레이션이라 SpringBootTest 없이도 충분히 검증된다.
 */
class EmbeddingReindexServiceTest {

    private final FreelancerDirectoryPort freelancerDirectoryPort = Mockito.mock(FreelancerDirectoryPort.class);
    private final ProjectDirectoryPort projectDirectoryPort = Mockito.mock(ProjectDirectoryPort.class);
    private final MatchingPort matchingPort = Mockito.mock(MatchingPort.class);
    private final MatchingRoundRepository matchingRoundRepository = Mockito.mock(MatchingRoundRepository.class);

    private final EmbeddingReindexService service = new EmbeddingReindexService(
            freelancerDirectoryPort, projectDirectoryPort, matchingPort, matchingRoundRepository,
            new FreelancerEmbeddingRefresher(freelancerDirectoryPort, matchingPort));

    @Test
    @DisplayName("이력서 있는 프리랜서 전체 + 모집 시작한 포지션 전체를 다시 임베딩한다")
    void reindexesAllFreelancersAndPositions() {
        when(freelancerDirectoryPort.findAllFreelancerIdsWithResume()).thenReturn(List.of(1L, 2L));
        when(freelancerDirectoryPort.findResumeSummary(1L))
                .thenReturn(new FreelancerResumeSummary("자기소개1", List.of(), List.of()));
        when(freelancerDirectoryPort.findResumeSummary(2L))
                .thenReturn(new FreelancerResumeSummary("자기소개2", List.of(), List.of()));

        when(matchingRoundRepository.findLatestRoundsByDistinctPosition()).thenReturn(List.of(
                round(1L, 10L, 100L),
                round(2L, 20L, 200L)
        ));
        when(projectDirectoryPort.findPositionSummary(10L, 100L)).thenReturn(positionSummary(10L));
        when(projectDirectoryPort.findPositionSummary(20L, 200L)).thenReturn(positionSummary(20L));

        EmbeddingReindexResult result = service.reindexAll();

        verify(matchingPort).upsertFreelancerEmbedding(eq(1L), Mockito.contains("자기소개1"));
        verify(matchingPort).upsertFreelancerEmbedding(eq(2L), Mockito.contains("자기소개2"));
        verify(matchingPort).upsertPositionEmbedding(eq(100L), Mockito.anyString());
        verify(matchingPort).upsertPositionEmbedding(eq(200L), Mockito.anyString());

        assertThat(result.freelancerSuccessCount()).isEqualTo(2);
        assertThat(result.freelancerFailCount()).isEqualTo(0);
        assertThat(result.positionSuccessCount()).isEqualTo(2);
        assertThat(result.positionFailCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 진행하고 실패 건수로 센다")
    void continuesWhenOneItemFails() {
        when(freelancerDirectoryPort.findAllFreelancerIdsWithResume()).thenReturn(List.of(1L, 2L));
        when(freelancerDirectoryPort.findResumeSummary(1L))
                .thenReturn(new FreelancerResumeSummary("자기소개1", List.of(), List.of()));
        when(freelancerDirectoryPort.findResumeSummary(2L))
                .thenThrow(new RuntimeException("조회 실패"));

        when(matchingRoundRepository.findLatestRoundsByDistinctPosition())
                .thenReturn(List.of(round(1L, 10L, 100L)));
        when(projectDirectoryPort.findPositionSummary(10L, 100L))
                .thenThrow(new RuntimeException("조회 실패"));

        EmbeddingReindexResult result = service.reindexAll();

        verify(matchingPort).upsertFreelancerEmbedding(eq(1L), Mockito.anyString());
        verify(matchingPort, Mockito.never()).upsertFreelancerEmbedding(eq(2L), Mockito.anyString());
        verify(matchingPort, Mockito.never()).upsertPositionEmbedding(eq(100L), Mockito.anyString());

        assertThat(result.freelancerSuccessCount()).isEqualTo(1);
        assertThat(result.freelancerFailCount()).isEqualTo(1);
        assertThat(result.positionSuccessCount()).isEqualTo(0);
        assertThat(result.positionFailCount()).isEqualTo(1);
    }

    private static ProjectPositionSummary positionSummary(Long projectId) {
        return new ProjectPositionSummary(
                projectId, "프로젝트" + projectId, "회사명", "IT/50명", JobRole.BACKEND, List.of(),
                3, "재택/풀타임", "4개월", 4, PeriodUnit.MONTH, null, 50_000_000L,
                1, 1, "진행상황", "메인업무", "상세범위", "우대사항"
        );
    }

    private static MatchingRound round(Long id, Long projectId, Long positionId) {
        return MatchingRound.reconstitute(id, projectId, positionId, 1, RecommendationType.INITIAL,
                null, 0L, 1, 3, false, MatchingRoundStatus.COMPLETED);
    }
}
