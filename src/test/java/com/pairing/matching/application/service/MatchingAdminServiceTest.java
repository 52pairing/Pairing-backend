package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.MatchingAdminReaderPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.result.admin.AiLogItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import com.pairing.matching.application.result.admin.EmbeddingMissingSummary;
import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingAdminServiceTest {

    private final MatchingAdminReaderPort matchingAdminReaderPort = Mockito.mock(MatchingAdminReaderPort.class);
    private final FreelancerEmbeddingRefresher freelancerEmbeddingRefresher =
            Mockito.mock(FreelancerEmbeddingRefresher.class);
    private final ProjectDirectoryPort projectDirectoryPort = Mockito.mock(ProjectDirectoryPort.class);
    private final MatchingPort matchingPort = Mockito.mock(MatchingPort.class);

    private final MatchingAdminService service = new MatchingAdminService(
            matchingAdminReaderPort, freelancerEmbeddingRefresher, projectDirectoryPort, matchingPort);

    @Test
    @DisplayName("임베딩 누락 대상 조회는 관리자 리더 포트에 위임한다")
    void delegatesMissingEmbeddingSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        EmbeddingMissingResult expected = new EmbeddingMissingResult(
                new EmbeddingMissingSummary(1, 2),
                new PageImpl<>(List.of(new EmbeddingMissingItem(
                        "FREELANCER", 9L, "김길동", "ACTIVE", "NO_EMBEDDING",
                        null, "SUCCESS", LocalDateTime.of(2026, 8, 12, 7, 31)
                )), pageable, 1)
        );
        when(matchingAdminReaderPort.findMissingEmbeddings("ALL", pageable)).thenReturn(expected);

        EmbeddingMissingResult result = service.findMissingEmbeddings("ALL", pageable);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("프리랜서 개별 재색인은 프리랜서 임베딩 리프레셔를 호출한다")
    void reindexesFreelancerEmbedding() {
        service.reindexFreelancer(9L);

        verify(freelancerEmbeddingRefresher).refreshByFreelancerId(9L);
    }

    @Test
    @DisplayName("프리랜서 개별 재색인 실패는 관리자 API 응답 흐름을 깨지 않는다")
    void doesNotPropagateFreelancerReindexFailure() {
        doThrow(new RuntimeException("embedding failed"))
                .when(freelancerEmbeddingRefresher).refreshByFreelancerId(9L);

        assertThatNoException().isThrownBy(() -> service.reindexFreelancer(9L));
    }

    @Test
    @DisplayName("포지션 개별 재색인은 포지션 요약으로 임베딩 텍스트를 만들어 저장한다")
    void reindexesPositionEmbedding() {
        when(projectDirectoryPort.findPositionSummary(23L, 33L)).thenReturn(positionSummary());

        service.reindexPosition(23L, 33L);

        verify(projectDirectoryPort).findPositionSummary(23L, 33L);
        verify(matchingPort).upsertPositionEmbedding(eq(33L), anyString());
    }

    @Test
    @DisplayName("포지션 개별 재색인 실패는 관리자 API 응답 흐름을 깨지 않는다")
    void doesNotPropagatePositionReindexFailure() {
        when(projectDirectoryPort.findPositionSummary(23L, 33L))
                .thenThrow(new RuntimeException("position missing"));

        assertThatNoException().isThrownBy(() -> service.reindexPosition(23L, 33L));
    }

    @Test
    @DisplayName("AI 로그 조회는 관리자 리더 포트에 위임한다")
    void delegatesAiLogSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AiLogItem> expected = new PageImpl<>(List.of(new AiLogItem(
                70L, "EMBEDDING", "POSITION", 33L, "SUCCESS", null,
                LocalDateTime.of(2026, 8, 12, 7, 31)
        )), pageable, 1);
        when(matchingAdminReaderPort.findAiLogs(
                "EMBEDDING", "POSITION", 33L, "SUCCESS", null, null, pageable
        )).thenReturn(expected);

        Page<AiLogItem> result = service.findAiLogs(
                "EMBEDDING", "POSITION", 33L, "SUCCESS", null, null, pageable);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("매칭 상태 디버깅 조회는 관리자 리더 포트에 위임한다")
    void delegatesDiagnosticsSearch() {
        MatchingDiagnostics expected = diagnostics();
        when(matchingAdminReaderPort.findDiagnostics(23L, 33L)).thenReturn(expected);

        MatchingDiagnostics result = service.findDiagnostics(23L, 33L);

        assertThat(result).isSameAs(expected);
    }

    private static ProjectPositionSummary positionSummary() {
        return new ProjectPositionSummary(
                23L, "웹 개발자. 자바", "(주)승재컴퍼니", "IT/컨텐츠/AI · 10~49명",
                JobRole.BACKEND, List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT),
                3, "재택 · 풀타임", "6개월", 6, PeriodUnit.MONTH,
                LocalDate.of(2026, 9, 1), 50_000_000L, 2, 1,
                "개발 중", "백엔드 API 개발", "상세 범위", "추가 사항"
        );
    }

    private static MatchingDiagnostics diagnostics() {
        return new MatchingDiagnostics(
                new MatchingDiagnostics.ProjectInfo(23L, "웹 개발자. 자바", "RECRUITING", "DEPOSIT_PAID"),
                new MatchingDiagnostics.PositionInfo(33L, "RECRUITING", "DEVELOPMENT", "BACKEND"),
                new MatchingDiagnostics.SnapshotInfo(true, true),
                new MatchingDiagnostics.EmbeddingInfo(true, "gemini-embedding-001", 1),
                new MatchingDiagnostics.RoundInfo(14L, 1, "INITIAL", "COMPLETED"),
                new MatchingDiagnostics.CountInfo(1, 1, 1),
                new MatchingDiagnostics.LastAiLogInfo("SUCCESS", LocalDateTime.of(2026, 8, 12, 7, 31), null)
        );
    }
}
