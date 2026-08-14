package com.pairing.matching.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.matching.application.usecase.EmbeddingReindexUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateCommandUseCase;
import com.pairing.matching.application.usecase.MatchingCandidateQueryUseCase;
import com.pairing.matching.application.usecase.MatchingRequestCommandUseCase;
import com.pairing.matching.application.usecase.MatchingRequestQueryUseCase;
import com.pairing.matching.application.usecase.MatchingRerecommendUseCase;
import com.pairing.matching.domain.model.MatchingRequestTab;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.presentation.api.request.MatchingRejectRequest;
import com.pairing.matching.presentation.api.request.MatchingRequestCreateRequest;
import com.pairing.matching.presentation.api.request.RerecommendRequest;
import com.pairing.matching.presentation.api.response.CandidateListResponse;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 매칭 후보 조회와 매칭 요청. (요구사항 R01~R05, R19)
 *
 * <p>후보 추천 자체는 AI 서버(FastAPI)가 계산하고, 이 API 는 그 결과를 저장·조회하는 창구다.
 * 상태는 프리랜서 개인이 아니라 매칭 요청 1건에 붙는다.
 */
@RestController
@RequestMapping("/api/v1/matchings")
@RequiredArgsConstructor
@Tag(name = "11. Matching", description = "AI 매칭 후보/요청 API")
public class MatchingController {

    private final MatchingCandidateQueryUseCase matchingCandidateQueryUseCase;
    private final MatchingCandidateCommandUseCase matchingCandidateCommandUseCase;
    private final MatchingRequestCommandUseCase matchingRequestCommandUseCase;
    private final MatchingRequestQueryUseCase matchingRequestQueryUseCase;
    private final MatchingRerecommendUseCase matchingRerecommendUseCase;
    private final EmbeddingReindexUseCase embeddingReindexUseCase;

    @GetMapping("/positions/{positionId}/candidates")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "추천 후보 조회",
            description = "이 포지션에 지금까지 노출된 후보 전부를 반환합니다(재추천으로 회차가 늘어도 이전 후보가 사라지지 않습니다). "
                    + "정렬은 최신 회차 먼저, 회차 안에서는 순위 순입니다 — 받은 순서 그대로 그리면 됩니다. "
                    + "선택·거절한 후보도 목록에 남고 status(AVAILABLE/REQUESTED/REJECTED)로 구분됩니다. "
                    + "추천을 아직 만드는 중이면 preparing=true, 생성이 실패했으면 failed=true 입니다(에러가 아닙니다).")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<CandidateListResponse>> findCandidates(
            @PathVariable Long positionId,
            @CurrentAccountId Long accountId
    ) {
        CandidateListResponse response = matchingCandidateQueryUseCase.findCandidates(positionId, accountId);
        return ResponseEntity.ok(ApiResponse.success("CANDIDATES_FOUND", "조회에 성공했습니다.", response));
    }

    @PostMapping("/candidates/{candidateId}/rejection")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "추천 후보 거절",
            description = "해당 후보와는 매칭을 진행하지 않습니다. 거절한 후보는 다시 추천되지 않습니다. "
                    + "무료 재추천은 이 거절과 무관하게, 발송한 매칭 요청이 전원 거절·만료됐을 때 프로젝트 전체 기준 1회 주어집니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<CandidateListResponse>> rejectCandidate(
            @PathVariable Long candidateId,
            @CurrentAccountId Long accountId
    ) {
        CandidateListResponse response = matchingCandidateCommandUseCase.rejectCandidate(candidateId, accountId);
        return ResponseEntity.ok(ApiResponse.success("CANDIDATE_REJECTED", "후보를 거절했습니다.", response));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "매칭 요청 발송",
            description = "선택한 후보에게 요청을 보냅니다. 모집 인원을 초과해 선택할 수 없고, 3일 뒤 자동 만료됩니다.")
    public ResponseEntity<ApiResponse<List<MatchingRequestResponse>>> sendRequests(
            @Valid @RequestBody MatchingRequestCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        List<MatchingRequestResponse> responses = matchingRequestCommandUseCase.sendRequests(
                request.positionId(), request.candidateIds(), accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("MATCHING_REQUESTED", "매칭 요청을 보냈습니다.", responses));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "보낸 매칭 요청 목록", description = "프로젝트 또는 포지션 기준으로 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<MatchingRequestResponse>>> findSentRequests(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(required = false) MatchingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        PageResponse<MatchingRequestResponse> response = matchingRequestQueryUseCase.findSentRequests(
                projectId, positionId, status, page, size, accountId);
        return ResponseEntity.ok(ApiResponse.success("REQUESTS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/requests/received")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "받은 매칭 요청 목록", description = "프리랜서 메인의 '요청받은 프로젝트'에 해당합니다.")
    public ResponseEntity<ApiResponse<PageResponse<MatchingRequestResponse>>> findReceivedRequests(
            @RequestParam(defaultValue = "ALL") MatchingRequestTab tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        PageResponse<MatchingRequestResponse> response = matchingRequestQueryUseCase.findReceivedRequests(
                tab, page, size, accountId);
        return ResponseEntity.ok(ApiResponse.success("REQUESTS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "매칭 요청 상세", description = "요청에 걸린 프로젝트 조건과 상대 정보를 함께 봅니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> findRequest(
            @PathVariable Long requestId,
            @CurrentAccountId Long accountId
    ) {
        MatchingRequestResponse response = matchingRequestQueryUseCase.findRequest(requestId, accountId);
        return ResponseEntity.ok(ApiResponse.success("REQUEST_FOUND", "조회에 성공했습니다.", response));
    }

    @PostMapping("/requests/{requestId}/acceptance")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 요청 수락",
            description = "수락하면 해당 포지션 협상방이 생성되고 협상이 시작됩니다. 전원 수락을 기다리지 않습니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> accept(
            @PathVariable Long requestId,
            @CurrentAccountId Long accountId
    ) {
        MatchingRequestResponse response = matchingRequestCommandUseCase.accept(requestId, accountId);
        return ResponseEntity.ok(ApiResponse.success("MATCHING_ACCEPTED", "요청을 수락했습니다.", response));
    }

    @PostMapping("/requests/{requestId}/rejection")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 요청 거절", description = "거절하면 해당 프로젝트의 재추천 대상에서 제외됩니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> reject(
            @PathVariable Long requestId,
            @Valid @RequestBody MatchingRejectRequest request,
            @CurrentAccountId Long accountId
    ) {
        MatchingRequestResponse response = matchingRequestCommandUseCase.reject(requestId, request.reason(),
                accountId);
        return ResponseEntity.ok(ApiResponse.success("MATCHING_REJECTED", "요청을 거절했습니다.", response));
    }

    @PostMapping("/positions/{positionId}/rerecommendations")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "재추천 요청",
            description = "FREE 는 요청 후보 전원 거절 시 프로젝트당 1회, PAID 는 최대 5회(1명당 10,000원)입니다. "
                    + "이전에 노출된 후보는 제외됩니다. "
                    + "**후보 목록을 바로 돌려주지 않고 202로 응답합니다** — AI 추천에 수 초~수십 초가 걸려서입니다. "
                    + "완료되면 `MATCHING_RECOMMENDED` 알림이 가므로, 알림을 받은 뒤 추천 후보 조회 API를 "
                    + "다시 호출하면 됩니다. 한도 초과·모집 종료 같은 검증 실패는 이 응답에서 바로 알려줍니다.")
    public ResponseEntity<ApiResponse<Void>> rerecommend(
            @PathVariable Long positionId,
            @Valid @RequestBody RerecommendRequest request,
            @CurrentAccountId Long accountId
    ) {
        matchingRerecommendUseCase.rerecommend(positionId, request.type(), request.quantity(), accountId);
        return ResponseEntity.accepted()
                .body(ApiResponse.accepted("RERECOMMEND_STARTED", "재추천을 시작했습니다. 완료되면 알려드릴게요."));
    }

    @PostMapping("/admin/embeddings/reindex")
    @Operation(summary = "[관리자] 임베딩 일괄 재색인",
            description = "이력서 있는 프리랜서 전체 + 모집 시작한 포지션 전체의 임베딩을 다시 생성합니다. "
                    + "임베딩 모델을 교체해 벡터 공간이 달라졌을 때 씁니다. "
                    + "**백그라운드로 처리하고 즉시 응답합니다** — 대상 1건마다 외부 AI 호출이 일어나 "
                    + "전체가 몇 분씩 걸릴 수 있어서입니다. 성공·실패 건수는 완료 시점에 서버 로그로 남습니다.")
    public ResponseEntity<ApiResponse<Void>> reindexEmbeddings() {
        embeddingReindexUseCase.startReindexAll();
        return ResponseEntity.accepted()
                .body(ApiResponse.accepted("EMBEDDINGS_REINDEX_STARTED", "임베딩 재색인을 시작했습니다."));
    }
}
