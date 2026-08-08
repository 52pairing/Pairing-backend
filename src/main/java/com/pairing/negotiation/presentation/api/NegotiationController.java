package com.pairing.negotiation.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationAdminQueryUseCase;
import com.pairing.negotiation.application.usecase.NegotiationLoopUseCase;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.negotiation.presentation.api.support.NegotiationAdminResponseFactory;
import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.presentation.api.response.NegotiationLogIntegrityResponse;
import com.pairing.negotiation.domain.model.NegotiationMessageType;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.presentation.api.support.NegotiationResponseFactory;
import com.pairing.negotiation.presentation.api.request.NegotiationAnswerRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationFinalApprovalRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationGiveUpRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationStartRequest;
import com.pairing.negotiation.presentation.api.response.AgentRawLogResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminDetailResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminSummaryResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationMessageResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationSummaryResponse;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A2A 협상. (요구사항 R06~R12, R41)
 *
 * <p>AI 에이전트가 제안을 만들고 사람은 조건별로 응답한다. 협상 중에는 자유 입력이 아니라
 * 숫자 입력과 선택지만 받는다. 모든 조건이 합의되면 AI 가 빠지고(AI Out) 사람 채팅으로 넘어간다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/negotiations")
@RequiredArgsConstructor
@Tag(name = "12. Negotiation", description = "A2A 협상 API")
public class NegotiationController {

    private final NegotiationQueryUseCase negotiationQueryUseCase;
    private final NegotiationLoopUseCase negotiationLoopUseCase;
    private final NegotiationAdminQueryUseCase negotiationAdminQueryUseCase;

    @GetMapping("/mine")
    @Operation(summary = "내 협상 목록",
            description = "클라이언트·프리랜서 모두 자기 기준으로 조회합니다. projectId 를 주면 해당 프로젝트의 협상만"
                    + " 반환합니다(클라이언트 협상 탭). 카드에 라운드 X/15·lastProposalBy·lastProposalAt 를 표시합니다.")
    public ResponseEntity<ApiResponse<PageResponse<NegotiationSummaryResponse>>> findMine(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) NegotiationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        PageResponse<NegotiationSummaryResponse> result = PageResponse.from(
                negotiationQueryUseCase.findMine(accountId, projectId, status, pageable)
                        .map(NegotiationResponseFactory::summary));
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATIONS_FOUND", "조회에 성공했습니다.", result));
    }

    @GetMapping("/{negotiationId}")
    @Operation(summary = "협상 상세", description = "협상 대상 조건과 현재 라운드를 반환합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<NegotiationResponse>> findOne(
            @PathVariable Long negotiationId,
            @CurrentAccountId Long accountId
    ) {
        NegotiationView view = negotiationQueryUseCase.getDetail(negotiationId, accountId);
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_FOUND", "조회에 성공했습니다.",
                NegotiationResponseFactory.detail(view)));
    }

    @GetMapping("/{negotiationId}/messages")
    @Operation(summary = "협상 로그 조회",
            description = "AI 제안·근거·응답이 시간순으로 남습니다. 협상 중에도 조회할 수 있습니다.")
    public ResponseEntity<ApiResponse<List<NegotiationMessageResponse>>> findMessages(
            @PathVariable Long negotiationId,
            @CurrentAccountId Long accountId
    ) {
        // 당사자 검증 + 조건 타입 매핑을 위해 상세를 먼저 얻는다(로그의 conditionType 해석용).
        NegotiationView view = negotiationQueryUseCase.getDetail(negotiationId, accountId);
        Map<Long, ConditionType> typeById = view.negotiation().getConditions().stream()
                .collect(Collectors.toMap(NegotiationCondition::getId, NegotiationCondition::getConditionType));

        List<NegotiationMessageResponse> logs = negotiationQueryUseCase.findMessages(negotiationId, accountId)
                .stream()
                .map(m -> NegotiationResponseFactory.message(m, typeById.get(m.getConditionId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("MESSAGES_FOUND", "조회에 성공했습니다.", logs));
    }

    @GetMapping("/{negotiationId}/log-integrity")
    @Operation(summary = "협상 로그 무결성 검증",
            description = "협상 로그(증거)의 해시 체인을 재계산해 위변조 여부를 확인합니다. 분쟁 시 "
                    + "로그가 조작되지 않았음을 증명하는 용도입니다.")
    public ResponseEntity<ApiResponse<NegotiationLogIntegrityResponse>> verifyLogIntegrity(
            @PathVariable Long negotiationId,
            @CurrentAccountId Long accountId
    ) {
        NegotiationLogVerifier.Result result = negotiationQueryUseCase.verifyLog(negotiationId, accountId);
        return ResponseEntity.ok(ApiResponse.success("LOG_INTEGRITY_CHECKED", "검증을 완료했습니다.",
                new NegotiationLogIntegrityResponse(
                        result.valid(), result.brokenAtMessageId(), result.checked())));
    }

    @PostMapping("/{negotiationId}/start")
    @Operation(summary = "협상 시작 (마지노선 설정)",
            description = "상대 AI 의 초기 제안을 확인한 뒤 쟁점별 최소 조건을 정해 협상을 시작합니다. "
                    + "마지노선은 상대에게 노출되지 않고 내 에이전트의 하한으로만 쓰입니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<NegotiationResponse>> start(
            @PathVariable Long negotiationId,
            @Valid @RequestBody NegotiationStartRequest request,
            @CurrentAccountId Long accountId
    ) {
        List<NegotiationLoopUseCase.FloorInput> floors = request.conditions().stream()
                .map(c -> new NegotiationLoopUseCase.FloorInput(c.conditionType(), c.value()))
                .toList();
        negotiationLoopUseCase.start(negotiationId, accountId, floors);
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_STARTED", "협상을 시작했습니다.",
                NegotiationResponseFactory.detail(negotiationQueryUseCase.getDetail(negotiationId, accountId))));
    }

    @PostMapping("/{negotiationId}/answers")
    @Operation(summary = "조건 응답 제출",
            description = "AI 가 묶어서 보낸 조건들에 한 번에 응답합니다. 거절 시 직접 입력값이 필요합니다. 응답하면 다음 AI 제안이 생성됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<NegotiationResponse>> answer(
            @PathVariable Long negotiationId,
            @Valid @RequestBody NegotiationAnswerRequest request,
            @CurrentAccountId Long accountId
    ) {
        List<NegotiationLoopUseCase.AnswerInput> answers = request.answers().stream()
                .map(a -> new NegotiationLoopUseCase.AnswerInput(a.conditionId(), a.accepted(), a.proposedValue()))
                .toList();
        negotiationLoopUseCase.answer(negotiationId, accountId, request.roundNo(), answers);
        return ResponseEntity.ok(ApiResponse.success("ANSWER_SUBMITTED", "응답을 제출했습니다.",
                NegotiationResponseFactory.detail(negotiationQueryUseCase.getDetail(negotiationId, accountId))));
    }

    @Hidden
    @Deprecated
    @PostMapping("/{negotiationId}/final-approval")
    @Operation(summary = "[미사용] 최종 승인/거부",
            description = "[미사용] 15회 소진 시 자동 결렬(NEGOTIATION_FAILED) 채택으로 폐기. 양측 최종 승인 단계와"
                    + " negotiation_approval 테이블은 쓰지 않는다. 제거 예정.")
    public ResponseEntity<ApiResponse<NegotiationResponse>> finalApprove(
            @PathVariable Long negotiationId,
            @Valid @RequestBody NegotiationFinalApprovalRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 승인 기록 -> 양측 승인 시 AGREED, 한쪽 거부 시 FAILED
        return ResponseEntity.ok(ApiResponse.success("FINAL_APPROVAL_SUBMITTED", "제출했습니다.", sampleDetail()));
    }

    @PostMapping("/{negotiationId}/give-up")
    @Operation(summary = "협상 포기", description = "즉시 협상 결렬로 종료됩니다. 클라이언트는 유료 재추천으로 다시 찾아야 합니다.")
    public ResponseEntity<ApiResponse<NegotiationResponse>> giveUp(
            @PathVariable Long negotiationId,
            @Valid @RequestBody NegotiationGiveUpRequest request,
            @CurrentAccountId Long accountId
    ) {
        negotiationLoopUseCase.giveUp(negotiationId, accountId, request.reason());
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_GAVE_UP", "협상을 종료했습니다.",
                NegotiationResponseFactory.detail(negotiationQueryUseCase.getDetail(negotiationId, accountId))));
    }

    // ==========================================
    // 관리자 (R41)
    // ==========================================

    @GetMapping("/admin/summary")
    @Operation(summary = "[관리자] 협상 요약", description = "AI Agent 관리 화면 상단 카드입니다.")
    public ResponseEntity<ApiResponse<NegotiationAdminSummaryResponse>> findSummaryForAdmin() {
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_SUMMARY_FOUND", "조회에 성공했습니다.",
                NegotiationAdminResponseFactory.summary(negotiationAdminQueryUseCase.getSummary())));
    }

    @GetMapping("/admin")
    @Operation(summary = "[관리자] 협상 목록",
            description = "프로젝트명·클라이언트·프리랜서로 검색하고 상태로 필터링합니다.")
    public ResponseEntity<ApiResponse<PageResponse<NegotiationSummaryResponse>>> findAllForAdmin(
            @RequestParam(required = false) NegotiationStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<NegotiationSummaryResponse> result = PageResponse.from(
                negotiationAdminQueryUseCase.search(keyword, status, pageable)
                        .map(NegotiationAdminResponseFactory::listItem));
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATIONS_FOUND", "조회에 성공했습니다.", result));
    }

    @GetMapping("/admin/{negotiationId}")
    @Operation(summary = "[관리자] 협상 상세",
            description = "기본 정보와 최종 협상 결과, 라운드별 제안·응답 로그를 반환합니다. 화면의 '협상 로그' 탭입니다.")
    public ResponseEntity<ApiResponse<NegotiationAdminDetailResponse>> findOneForAdmin(
            @PathVariable Long negotiationId
    ) {
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_FOUND", "조회에 성공했습니다.",
                NegotiationAdminResponseFactory.detail(negotiationAdminQueryUseCase.getDetail(negotiationId))));
    }

    @GetMapping("/admin/{negotiationId}/raw-logs")
    @Operation(summary = "[관리자] AI 원본 로그",
            description = "ai_agent_log 를 가공 없이 반환합니다. 화면의 '원본 로그' 탭이며 장애 분석용입니다.")
    public ResponseEntity<ApiResponse<List<AgentRawLogResponse>>> findRawLogsForAdmin(
            @PathVariable Long negotiationId
    ) {
        List<AgentRawLogResponse> logs = negotiationAdminQueryUseCase.getRawLogs(negotiationId).stream()
                .map(NegotiationAdminResponseFactory::rawLog)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("RAW_LOGS_FOUND", "조회에 성공했습니다.", logs));
    }

    @GetMapping("/admin/{negotiationId}/messages")
    @Operation(summary = "[관리자] AI 협상 로그 조회", description = "A2A 협상 과정에서 생성된 제안·이유·응답을 봅니다.")
    public ResponseEntity<ApiResponse<List<NegotiationMessageResponse>>> findMessagesForAdmin(
            @PathVariable Long negotiationId
    ) {
        NegotiationAdminQueryUseCase.AdminMessages result = negotiationAdminQueryUseCase.getMessages(negotiationId);
        List<NegotiationMessageResponse> logs = result.messages().stream()
                .map(m -> NegotiationResponseFactory.message(m, result.conditionTypes().get(m.getConditionId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("MESSAGES_FOUND", "조회에 성공했습니다.", logs));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private NegotiationResponse sampleDetail() {
        NegotiationResponse.Condition condition = new NegotiationResponse.Condition(
                401L, ConditionType.AMOUNT, "20000000", "25000000", "22000000",
                "프리랜서 경력이 요구 수준을 넘어 중간값을 제안합니다.", null, ConditionStatus.PENDING, 2, "3500000");

        return new NegotiationResponse(300L, 1L, "페어링 웹 리뉴얼", 10L, "홍길동",
                NegotiationStatus.IN_PROGRESS, 3, 15, null, 500L, null, false, List.of(condition));
    }

    private NegotiationMessageResponse sampleMessage() {
        return new NegotiationMessageResponse(900L, 3, SenderType.CLIENT_AGENT,
                NegotiationMessageType.PROPOSAL, ConditionType.AMOUNT, "월 220만원을 제안합니다.",
                "클라이언트 예산 상한과 프리랜서 최저 수용가의 중간값입니다.", "2200000", null, LocalDateTime.now());
    }
}
