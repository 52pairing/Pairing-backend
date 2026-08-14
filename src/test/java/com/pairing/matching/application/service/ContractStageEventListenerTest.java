package com.pairing.matching.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.application.event.ProjectClosedEvent;
import com.pairing.project.application.event.ProjectCompletionRequestedEvent;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 체결 이후의 인원별 상태 전이를 확인한다.
 *
 * <p>이 리스너는 {@code matching_request}만 읽고 쓰므로 계정·약관·프로젝트 시딩 없이 요청 행만
 * 직접 넣어 검증한다(다른 리스너 테스트들이 무거운 건 실제 가입·결제 흐름까지 타기 때문이다).
 */
@SpringBootTest
class ContractStageEventListenerTest {

    private static final Long PROJECT_ID = 7301L;
    private static final Long OTHER_PROJECT_ID = 7302L;
    private static final Long POSITION_ID = 7301L;
    private static final Long FREELANCER_ID = 7301L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM matching_request WHERE project_id IN (?, ?)",
                PROJECT_ID, OTHER_PROJECT_ID);
    }

    @Test
    @DisplayName("계약 체결부터 프로젝트 종료까지 인원별 상태가 단계별로 따라간다")
    void advancesThroughEveryStage() {
        Long requestId = seedRequest(FREELANCER_ID, "CONTRACT_PENDING");

        eventPublisher.publishEvent(new ContractSignedEvent(1L, PROJECT_ID, POSITION_ID, FREELANCER_ID));
        assertThat(statusOf(requestId)).isEqualTo("CONTRACTED");

        eventPublisher.publishEvent(new ProjectProgressStartedEvent(PROJECT_ID));
        assertThat(statusOf(requestId)).isEqualTo("IN_PROGRESS");

        eventPublisher.publishEvent(new ProjectCompletionRequestedEvent(PROJECT_ID));
        assertThat(statusOf(requestId)).isEqualTo("COMPLETION_PENDING");

        eventPublisher.publishEvent(new ProjectClosedEvent(PROJECT_ID));
        assertThat(statusOf(requestId)).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("같은 프로젝트에 거절된 요청이 섞여 있어도 단계 전환이 터지지 않는다")
    void skipsTerminatedRequests() {
        Long contracted = seedRequest(FREELANCER_ID, "CONTRACTED");
        // 거절된 요청은 종결 상태라 advanceStatus에 넣으면 예외가 난다. 이벤트가 발행 도메인의
        // 트랜잭션 안에서 처리되므로, 안 걸러내면 착수금 결제까지 통째로 롤백된다.
        Long rejected = seedRequest(FREELANCER_ID + 1, "REJECTED");

        eventPublisher.publishEvent(new ProjectProgressStartedEvent(PROJECT_ID));

        assertThat(statusOf(contracted)).isEqualTo("IN_PROGRESS");
        assertThat(statusOf(rejected)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("이벤트가 중복으로 와도 상태가 두 번 넘어가지 않는다")
    void isIdempotentOnRedelivery() {
        Long requestId = seedRequest(FREELANCER_ID, "CONTRACTED");

        eventPublisher.publishEvent(new ProjectProgressStartedEvent(PROJECT_ID));
        eventPublisher.publishEvent(new ProjectProgressStartedEvent(PROJECT_ID));

        assertThat(statusOf(requestId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("계약 체결 이벤트는 해당 프리랜서의 요청 1건만 옮긴다")
    void contractSignedTouchesOnlyThatFreelancer() {
        Long signed = seedRequest(FREELANCER_ID, "CONTRACT_PENDING");
        Long other = seedRequest(FREELANCER_ID + 1, "CONTRACT_PENDING");

        eventPublisher.publishEvent(new ContractSignedEvent(1L, PROJECT_ID, POSITION_ID, FREELANCER_ID));

        assertThat(statusOf(signed)).isEqualTo("CONTRACTED");
        assertThat(statusOf(other)).isEqualTo("CONTRACT_PENDING");
    }

    @Test
    @DisplayName("프로젝트가 취소되면 응답 대기 중인 요청이 기한 만료로 종결된다")
    void expiresPendingRequestsOnProjectCanceled() {
        Long pending = seedRequest(FREELANCER_ID, "REQUEST_PENDING");

        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));

        assertThat(statusOf(pending)).isEqualTo("REJECTED");
        // 사유가 없으면 화면이 프리랜서의 직접 거절과 구분하지 못한다. advanceStatus로 바꾸면 여기가 null이 된다.
        assertThat(rejectReasonOf(pending)).isEqualTo("EXPIRED");
        assertThat(respondedAtSet(pending)).isTrue();
    }

    @Test
    @DisplayName("취소는 이미 응답한 요청을 건드리지 않는다")
    void projectCanceledLeavesRespondedRequestsAlone() {
        // expire()는 REQUEST_PENDING이 아니면 ALREADY_RESPONDED를 던진다. 상태로 좁혀 조회하지 않으면
        // 이 요청들 때문에 예외가 나고, 같은 트랜잭션이라 프로젝트 취소까지 통째로 롤백된다.
        Long negotiating = seedRequest(FREELANCER_ID + 1, "NEGOTIATING");
        Long contracted = seedRequest(FREELANCER_ID + 2, "CONTRACTED");
        Long rejected = seedRequest(FREELANCER_ID + 3, "REJECTED");
        Long pending = seedRequest(FREELANCER_ID, "REQUEST_PENDING");

        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));

        assertThat(statusOf(pending)).isEqualTo("REJECTED");
        assertThat(statusOf(negotiating)).isEqualTo("NEGOTIATING");
        assertThat(statusOf(contracted)).isEqualTo("CONTRACTED");
        assertThat(statusOf(rejected)).isEqualTo("REJECTED");
        assertThat(rejectReasonOf(rejected)).isNull();
    }

    @Test
    @DisplayName("취소 이벤트가 중복으로 와도 두 번 적용되지 않는다")
    void projectCanceledIsIdempotentOnRedelivery() {
        Long pending = seedRequest(FREELANCER_ID, "REQUEST_PENDING");

        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));
        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));

        assertThat(statusOf(pending)).isEqualTo("REJECTED");
        assertThat(rejectReasonOf(pending)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("취소 대상 요청이 없어도 조용히 넘어간다")
    void projectCanceledWithNoPendingRequestIsQuiet() {
        Long contracted = seedRequest(FREELANCER_ID, "CONTRACTED");

        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));

        assertThat(statusOf(contracted)).isEqualTo("CONTRACTED");
    }

    @Test
    @DisplayName("취소는 다른 프로젝트의 요청을 건드리지 않는다")
    void projectCanceledTouchesOnlyThatProject() {
        Long mine = seedRequest(FREELANCER_ID, "REQUEST_PENDING");
        Long other = seedRequestFor(OTHER_PROJECT_ID, FREELANCER_ID, "REQUEST_PENDING");

        eventPublisher.publishEvent(new ProjectCanceledEvent(PROJECT_ID));

        assertThat(statusOf(mine)).isEqualTo("REJECTED");
        assertThat(statusOf(other)).isEqualTo("REQUEST_PENDING");
    }

    private Long seedRequest(Long freelancerId, String status) {
        return seedRequestFor(PROJECT_ID, freelancerId, status);
    }

    private Long seedRequestFor(Long projectId, Long freelancerId, String status) {
        jdbcTemplate.update("INSERT INTO matching_request "
                        + "(project_id, position_id, candidate_id, freelancer_id, status, requested_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                projectId, POSITION_ID, freelancerId, freelancerId, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM matching_request WHERE project_id = ? AND freelancer_id = ?",
                Long.class, projectId, freelancerId);
    }

    private String statusOf(Long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM matching_request WHERE id = ?",
                String.class, requestId);
    }

    private String rejectReasonOf(Long requestId) {
        return jdbcTemplate.queryForObject("SELECT reject_reason FROM matching_request WHERE id = ?",
                String.class, requestId);
    }

    private boolean respondedAtSet(Long requestId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT responded_at IS NOT NULL FROM matching_request WHERE id = ?", Boolean.class, requestId));
    }
}
