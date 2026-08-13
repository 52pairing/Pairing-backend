package com.pairing.matching.presentation.api;

import com.pairing.matching.application.result.admin.AiLogItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import com.pairing.matching.application.result.admin.EmbeddingMissingSummary;
import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import com.pairing.matching.application.usecase.EmbeddingReindexUseCase;
import com.pairing.matching.application.usecase.MatchingAdminUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateCommandUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateQueryUseCase;
import com.pairing.matching.application.usecase.MatchingRequestCommandUseCase;
import com.pairing.matching.application.usecase.MatchingRequestQueryUseCase;
import com.pairing.matching.application.usecase.MatchingRerecommendUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchingAdminControllerTest {

    private final MatchingCandidateQueryUseCase matchingCandidateQueryUseCase =
            Mockito.mock(MatchingCandidateQueryUseCase.class);
    private final MatchingCandidateCommandUseCase matchingCandidateCommandUseCase =
            Mockito.mock(MatchingCandidateCommandUseCase.class);
    private final MatchingRequestCommandUseCase matchingRequestCommandUseCase =
            Mockito.mock(MatchingRequestCommandUseCase.class);
    private final MatchingRequestQueryUseCase matchingRequestQueryUseCase =
            Mockito.mock(MatchingRequestQueryUseCase.class);
    private final MatchingRerecommendUseCase matchingRerecommendUseCase =
            Mockito.mock(MatchingRerecommendUseCase.class);
    private final EmbeddingReindexUseCase embeddingReindexUseCase =
            Mockito.mock(EmbeddingReindexUseCase.class);
    private final MatchingAdminUseCase matchingAdminUseCase =
            Mockito.mock(MatchingAdminUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MatchingController controller = new MatchingController(
                matchingCandidateQueryUseCase,
                matchingCandidateCommandUseCase,
                matchingRequestCommandUseCase,
                matchingRequestQueryUseCase,
                matchingRerecommendUseCase,
                embeddingReindexUseCase,
                matchingAdminUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("관리자 임베딩 일괄 재색인 API는 202를 반환한다")
    void startsBulkReindex() throws Exception {
        mockMvc.perform(post("/api/v1/matchings/admin/embeddings/reindex"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("EMBEDDINGS_REINDEX_STARTED"));

        verify(embeddingReindexUseCase).startReindexAll();
    }

    @Test
    @DisplayName("관리자 임베딩 누락 대상 조회 API는 요약과 목록을 반환한다")
    void findsMissingEmbeddings() throws Exception {
        when(matchingAdminUseCase.findMissingEmbeddings(eq("ALL"), any(Pageable.class)))
                .thenReturn(new EmbeddingMissingResult(
                        new EmbeddingMissingSummary(1, 2),
                        new PageImpl<>(List.of(new EmbeddingMissingItem(
                                "FREELANCER", 9L, "김길동", "ACTIVE", "NO_EMBEDDING",
                                null, "SUCCESS", null
                        )), PageRequest.of(0, 20), 1)
                ));

        mockMvc.perform(get("/api/v1/matchings/admin/embeddings/missing")
                        .param("targetType", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSING_EMBEDDINGS_FOUND"))
                .andExpect(jsonPath("$.data.summary.freelancerMissingCount").value(1))
                .andExpect(jsonPath("$.data.summary.positionMissingCount").value(2))
                .andExpect(jsonPath("$.data.items.content[0].targetType").value("FREELANCER"));
    }

    @Test
    @DisplayName("관리자 프리랜서 개별 재색인 API는 202를 반환한다")
    void startsFreelancerReindex() throws Exception {
        mockMvc.perform(post("/api/v1/matchings/admin/embeddings/freelancers/9/reindex"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("FREELANCER_REINDEX_STARTED"));

        verify(matchingAdminUseCase).reindexFreelancer(9L);
    }

    @Test
    @DisplayName("관리자 포지션 개별 재색인 API는 202를 반환한다")
    void startsPositionReindex() throws Exception {
        mockMvc.perform(post("/api/v1/matchings/admin/embeddings/positions/33/reindex")
                        .param("projectId", "23"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("POSITION_REINDEX_STARTED"));

        verify(matchingAdminUseCase).reindexPosition(23L, 33L);
    }

    @Test
    @DisplayName("관리자 AI 로그 조회 API는 페이지 응답을 반환한다")
    void findsAiLogs() throws Exception {
        when(matchingAdminUseCase.findAiLogs(
                eq("EMBEDDING"), eq("POSITION"), eq(33L), eq("SUCCESS"),
                any(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(new AiLogItem(
                70L, "EMBEDDING", "POSITION", 33L, "SUCCESS", null,
                null
        )), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/matchings/admin/ai-logs")
                        .param("agentType", "EMBEDDING")
                        .param("refType", "POSITION")
                        .param("refId", "33")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AI_LOGS_FOUND"))
                .andExpect(jsonPath("$.data.content[0].logId").value(70))
                .andExpect(jsonPath("$.data.content[0].refType").value("POSITION"));
    }

    @Test
    @DisplayName("관리자 매칭 상태 디버깅 API는 프로젝트/포지션 상태를 반환한다")
    void findsDiagnostics() throws Exception {
        when(matchingAdminUseCase.findDiagnostics(23L, 33L)).thenReturn(diagnostics());

        mockMvc.perform(get("/api/v1/matchings/admin/diagnostics")
                        .param("projectId", "23")
                        .param("positionId", "33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MATCHING_DIAGNOSTICS_FOUND"))
                .andExpect(jsonPath("$.data.project.projectId").value(23))
                .andExpect(jsonPath("$.data.position.positionId").value(33))
                .andExpect(jsonPath("$.data.counts.candidateCount").value(1));
    }

    private static MatchingDiagnostics diagnostics() {
        return new MatchingDiagnostics(
                new MatchingDiagnostics.ProjectInfo(23L, "웹 개발자. 자바", "RECRUITING", "DEPOSIT_PAID"),
                new MatchingDiagnostics.PositionInfo(33L, "RECRUITING", "DEVELOPMENT", "BACKEND"),
                new MatchingDiagnostics.SnapshotInfo(true, true),
                new MatchingDiagnostics.EmbeddingInfo(true, "gemini-embedding-001", 1),
                new MatchingDiagnostics.RoundInfo(14L, 1, "INITIAL", "COMPLETED"),
                new MatchingDiagnostics.CountInfo(1, 1, 1),
                new MatchingDiagnostics.LastAiLogInfo("SUCCESS", null, null)
        );
    }
}
