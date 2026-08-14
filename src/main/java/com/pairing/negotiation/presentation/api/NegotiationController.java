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
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.service.NegotiationLogVerifier;
import com.pairing.negotiation.presentation.api.response.NegotiationLogIntegrityResponse;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.presentation.api.support.NegotiationResponseFactory;
import com.pairing.negotiation.presentation.api.request.NegotiationAnswerRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationGiveUpRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationFloorUpdateRequest;
import com.pairing.negotiation.presentation.api.request.NegotiationStartRequest;
import com.pairing.negotiation.presentation.api.response.AgentRawLogResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminDetailResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminSummaryResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationMessageResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationSummaryResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationWaitingCountResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A2A 협상. (요구사항 R06~R12, R41)
 *
 * <p>AI 에이전트가 제안을 만들고 사람은 조건별로 응답한다. 협상 중에는 자유 입력이 아니라
 * 숫자 입력과 선택지만 받는다. 모든 조건이 합의되면 AI 가 빠지고(AI Out) 사람 채팅으로 넘어간다.
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

    @PatchMapping("/{negotiationId}/floors")
    @Operation(summary = "마지노선 재설정",
            description = "협상 중에 내 마지노선만 다시 긋습니다. 라운드가 오르지 않고 대리인도 돌지 않습니다. "
                    + "상대 제안이 내 선 밖이라 수락이 막혔을 때(NG_011) 선을 넓히는 용도입니다. "
                    + "이미 합의된 쟁점은 고칠 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<NegotiationResponse>> updateFloors(
            @PathVariable Long negotiationId,
            @Valid @RequestBody NegotiationFloorUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        List<NegotiationLoopUseCase.FloorInput> floors = request.conditions().stream()
                .map(c -> new NegotiationLoopUseCase.FloorInput(c.conditionType(), c.value()))
                .toList();
        negotiationLoopUseCase.updateFloors(negotiationId, accountId, floors);
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_FLOORS_UPDATED", "마지노선을 수정했습니다.",
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
                .map(a -> new NegotiationLoopUseCase.AnswerInput(a.conditionId(), a.accepted(), a.proposedValue(),
                        Boolean.TRUE.equals(a.acceptBelowFloor())))
                .toList();
        negotiationLoopUseCase.answer(negotiationId, accountId, request.roundNo(), answers);
        return ResponseEntity.ok(ApiResponse.success("ANSWER_SUBMITTED", "응답을 제출했습니다.",
                NegotiationResponseFactory.detail(negotiationQueryUseCase.getDetail(negotiationId, accountId))));
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

    @PostMapping("/{negotiationId}/read")
    @Operation(summary = "협상 읽음 처리",
            description = "협상 상세를 열람했음을 기록합니다. 매칭 요청 카드의 '확인하지 않은 새 제안 수' 배지 기준선이"
                    + " 현재로 갱신됩니다. 채팅 읽음 처리와 같은 패턴입니다(상세 조회와 분리된 명시적 호출).")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable Long negotiationId,
            @CurrentAccountId Long accountId
    ) {
        negotiationLoopUseCase.markRead(negotiationId, accountId);
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_READ", "읽음 처리했습니다."));
    }

    @GetMapping("/waiting-count")
    @Operation(summary = "응답 대기 협상 건수",
            description = "헤더 배지용. 내가 답해야 하는 협상(진행 중 + 이번 라운드 AI 제안에 내 응답이 없음) 건수를"
                    + " 돌려줍니다. 목록(/mine)은 페이징이라 1페이지만 받으면 숫자가 실제보다 작아지므로 별도로 셉니다."
                    + " 클라·프리 양쪽인 계정은 합산됩니다.")
    public ResponseEntity<ApiResponse<NegotiationWaitingCountResponse>> countWaiting(
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_WAITING_COUNT", "응답 대기 건수를 조회했습니다.",
                new NegotiationWaitingCountResponse(negotiationQueryUseCase.countWaitingForMe(accountId))));
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

}
