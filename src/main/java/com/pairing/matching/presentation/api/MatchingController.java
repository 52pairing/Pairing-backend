package com.pairing.matching.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.matching.domain.model.MatchingRequestTab;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.presentation.api.request.MatchingRejectRequest;
import com.pairing.matching.presentation.api.request.MatchingRequestCreateRequest;
import com.pairing.matching.presentation.api.request.RerecommendRequest;
import com.pairing.matching.presentation.api.response.CandidateListResponse;
import com.pairing.matching.presentation.api.response.CandidateResponse;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.SkillCode;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 매칭 후보 조회와 매칭 요청. (요구사항 R01~R05, R19)
 *
 * <p>후보 추천 자체는 AI 서버(FastAPI)가 계산하고, 이 API 는 그 결과를 저장·조회하는 창구다.
 * 상태는 프리랜서 개인이 아니라 매칭 요청 1건에 붙는다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/matchings")
@RequiredArgsConstructor
@Tag(name = "11. Matching", description = "AI 매칭 후보/요청 API")
public class MatchingController {

    @GetMapping("/positions/{positionId}/candidates")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "추천 후보 조회",
            description = "가장 최근 추천 라운드의 후보를 반환합니다. 노출 수는 모집 인원을 넘지 않으며 클라이언트 등급에 따라 달라집니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<CandidateListResponse>> findCandidates(
            @PathVariable Long positionId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 최신 라운드의 노출 대상 후보 조회
        return ResponseEntity.ok(ApiResponse.success("CANDIDATES_FOUND", "조회에 성공했습니다.", sampleCandidates()));
    }

    @PostMapping("/candidates/{candidateId}/rejection")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "추천 후보 거절",
            description = "해당 후보와는 매칭을 진행하지 않습니다. 거절한 후보는 다시 추천되지 않습니다. "
                    + "추천된 후보를 모두 거절하면 무료 재추천이 활성화됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<CandidateListResponse>> rejectCandidate(
            @PathVariable Long candidateId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 후보 rejected 처리 -> 전원 거절이면 무료 재추천 활성화
        return ResponseEntity.ok(ApiResponse.success("CANDIDATE_REJECTED", "후보를 거절했습니다.", sampleCandidates()));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "매칭 요청 발송",
            description = "선택한 후보에게 요청을 보냅니다. 모집 인원을 초과해 선택할 수 없고, 3일 뒤 자동 만료됩니다.")
    public ResponseEntity<ApiResponse<List<MatchingRequestResponse>>> sendRequests(
            @Valid @RequestBody MatchingRequestCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 인원 초과 검증 후 요청 생성 + 프리랜서 알림
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("MATCHING_REQUESTED", "매칭 요청을 보냈습니다.", List.of(sampleRequest())));
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
        // TODO: 클라이언트가 보낸 요청 조회
        return ResponseEntity.ok(ApiResponse.success("REQUESTS_FOUND", "조회에 성공했습니다.", samplePage(page, size)));
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
        // TODO: 프리랜서가 받은 요청 조회
        return ResponseEntity.ok(ApiResponse.success("REQUESTS_FOUND", "조회에 성공했습니다.", samplePage(page, size)));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "매칭 요청 상세", description = "요청에 걸린 프로젝트 조건과 상대 정보를 함께 봅니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> findRequest(
            @PathVariable Long requestId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 당사자만 열람 가능
        return ResponseEntity.ok(ApiResponse.success("REQUEST_FOUND", "조회에 성공했습니다.", sampleRequest()));
    }

    @PostMapping("/requests/{requestId}/acceptance")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 요청 수락",
            description = "수락하면 해당 포지션 협상방이 생성되고 협상이 시작됩니다. 전원 수락을 기다리지 않습니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> accept(
            @PathVariable Long requestId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 상태 ACCEPTED -> 협상 생성 -> NEGOTIATING
        return ResponseEntity.ok(ApiResponse.success("MATCHING_ACCEPTED", "요청을 수락했습니다.", sampleRequest()));
    }

    @PostMapping("/requests/{requestId}/rejection")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "매칭 요청 거절", description = "거절하면 해당 프로젝트의 재추천 대상에서 제외됩니다.")
    public ResponseEntity<ApiResponse<MatchingRequestResponse>> reject(
            @PathVariable Long requestId,
            @Valid @RequestBody MatchingRejectRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 상태 REJECTED, 클라이언트 알림
        return ResponseEntity.ok(ApiResponse.success("MATCHING_REJECTED", "요청을 거절했습니다.", sampleRequest()));
    }

    @PostMapping("/positions/{positionId}/rerecommendations")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "재추천 요청",
            description = "FREE 는 요청 후보 전원 거절 시 프로젝트당 1회, PAID 는 최대 5회(1명당 10,000원)입니다. 이전에 노출된 후보는 제외됩니다.")
    public ResponseEntity<ApiResponse<CandidateListResponse>> rerecommend(
            @PathVariable Long positionId,
            @Valid @RequestBody RerecommendRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 무료/유료 조건 검증 -> 결제(유료) -> AI 서버 재추천 -> 결과 저장
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("RERECOMMENDED", "재추천을 완료했습니다.", sampleCandidates()));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private CandidateListResponse sampleCandidates() {
        CandidateResponse candidate = new CandidateResponse(
                100L, 7L, "홍길동", "profiles/uuid.png", JobRole.BACKEND, 5, "SENIOR",
                4.5, 12, List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT), 87.5,
                List.of("요구 스킬 97% 일치", "경력 조건 충족", "재택 근무 선호", "유사 프로젝트 3건"),
                PayUnit.MONTHLY, 6_500_000L, 1, false, false);

        return new CandidateListResponse(10L, 5L, 1, RecommendationType.INITIAL, 2,
                false, 5, false, List.of(candidate));
    }

    private MatchingRequestResponse sampleRequest() {
        return new MatchingRequestResponse(200L, 1L, "B2B 주문 관리 서비스 리뉴얼", 10L, JobRole.FRONTEND,
                "홍길동", 94.0, "주식회사 오이랩", "IT/소프트웨어 · 50-100명",
                List.of(SkillCode.REACT, SkillCode.TYPESCRIPT), 3, "재택 · 풀타임", "4개월",
                LocalDate.of(2026, 9, 1), MatchingStatus.REQUEST_PENDING, 6_000_000L,
                LocalDateTime.now(), LocalDateTime.now().plusDays(3), null, null, null, null, null);
    }

    private PageResponse<MatchingRequestResponse> samplePage(int page, int size) {
        return new PageResponse<>(List.of(sampleRequest()), page, size, 1, 1, true, true);
    }
}
