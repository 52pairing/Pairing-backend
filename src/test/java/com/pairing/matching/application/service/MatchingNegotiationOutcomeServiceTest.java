package com.pairing.matching.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.usecase.MatchingNegotiationOutcomeUseCase;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * negotiation 도메인이 타결/결렬 결과를 매칭 요청에 반영할 때 쓰는
 * {@link MatchingNegotiationOutcomeUseCase} 검증. 실제 호출부(NegotiationLoopService)는
 * negotiation 도메인 쪽 구현이 아직 없어서, 여기서는 유스케이스 자체의 상태 전이만 확인한다.
 *
 * <p>{@code markNegotiationFailed}는 프로젝트 대표 단계 재계산({@code ProjectCommandUseCase.syncStage})도
 * 같이 호출하므로, 실존하는 프로젝트 행이 있어야 한다(없으면 PJ_001).
 */
@SpringBootTest
@Transactional
class MatchingNegotiationOutcomeServiceTest {

    private static final Long PROJECT_ID = 7301L;
    private static final Long POSITION_ID = 7301L;

    @Autowired
    private MatchingNegotiationOutcomeUseCase matchingNegotiationOutcomeUseCase;
    @Autowired
    private MatchingRequestRepository matchingRequestRepository;
    @Autowired
    private ProjectQueryUseCase projectQueryUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT INTO project (id, client_id, title, start_desired_date, start_negotiable, "
                        + "period_value, period_unit, budget_amount, work_style, work_form, current_situation, "
                        + "main_task, status, payment_status, total_headcount, confirmed_headcount, "
                        + "extension_count, free_rerecommend_used, paid_rerecommend_used) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                PROJECT_ID, 1L, "협상 결과 반영 테스트 프로젝트", LocalDate.now().plusDays(14), false,
                6, "MONTH", 60_000_000L, "ONSITE", "FULL_TIME", "현행 시스템 운영중", "백엔드 API 개발",
                "NEGOTIATING", "SUCCESS_FEE_PAID", 1, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("협상이 타결되면 매칭 요청이 계약 대기 상태로 바뀐다")
    void markNegotiationAgreedMovesToContractPending() {
        Long requestId = seedNegotiatingRequest();

        matchingNegotiationOutcomeUseCase.markNegotiationAgreed(requestId);

        MatchingRequest request = matchingRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(MatchingStatus.CONTRACT_PENDING);
    }

    @Test
    @DisplayName("협상이 결렬되면 매칭 요청이 협상 결렬 상태로 종결되고 프로젝트는 모집중으로 되돌아간다")
    void markNegotiationFailedMovesToNegotiationFailed() {
        Long requestId = seedNegotiatingRequest();

        matchingNegotiationOutcomeUseCase.markNegotiationFailed(requestId);

        MatchingRequest request = matchingRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(MatchingStatus.NEGOTIATION_FAILED);
        assertThat(projectQueryUseCase.findStatus(PROJECT_ID)).isEqualTo(ProjectStatus.RECRUITING);
    }

    @Test
    @DisplayName("존재하지 않는 requestId면 REQUEST_NOT_FOUND를 던진다")
    void markNegotiationAgreedThrowsWhenRequestNotFound() {
        assertThatThrownBy(() -> matchingNegotiationOutcomeUseCase.markNegotiationAgreed(999_999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("협상중이 아닌 요청은 타결 처리해도 INVALID_MATCHING_STATE로 거부된다")
    void markNegotiationAgreedThrowsWhenNotNegotiating() {
        Long requestId = matchingRequestRepository.save(
                MatchingRequest.create(PROJECT_ID, POSITION_ID, 1L, 1L)).getId();

        assertThatThrownBy(() -> matchingNegotiationOutcomeUseCase.markNegotiationAgreed(requestId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MatchingErrorCode.INVALID_MATCHING_STATE);
    }

    private Long seedNegotiatingRequest() {
        MatchingRequest request = MatchingRequest.create(PROJECT_ID, POSITION_ID, 1L, 1L);
        request.accept();
        request.advanceStatus(MatchingStatus.NEGOTIATING);
        return matchingRequestRepository.save(request).getId();
    }
}
