package com.pairing.review.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReviewVisibility;
import com.pairing.review.presentation.api.request.ReviewCreateRequest;
import com.pairing.review.presentation.api.request.SiteReviewVisibilityRequest;
import com.pairing.review.presentation.api.response.ReviewResponse;
import com.pairing.review.presentation.api.response.ReviewSummaryResponse;
import com.pairing.review.presentation.api.response.SiteReviewResponse;
import com.pairing.review.presentation.api.response.SiteReviewSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 상호 평가와 사이트 후기. (요구사항 R22, R40)
 *
 * <p>대금 지급이 끝난 계약에 대해서만 작성할 수 있고, 한 번 쓰면 수정·삭제할 수 없다.
 * 상대 평가와 사이트 후기를 한 화면에서 작성하므로 등록 API 도 하나다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "16. Review", description = "평점/리뷰 API")
public class ReviewController {

    @PostMapping
    @Operation(summary = "리뷰 작성",
            description = "상대 평가(필수)와 사이트 후기(선택)를 함께 등록합니다. 작성 후 수정·삭제할 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody ReviewCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 계약 종료·대금 지급 확인 -> 중복 작성 차단 -> 상호 리뷰 + 사이트 리뷰(비공개) 저장
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("REVIEW_CREATED", "리뷰를 등록했습니다.", sampleReview()));
    }

    @GetMapping("/received")
    @Operation(summary = "받은 리뷰 목록", description = "상대가 나에게 남긴 평가입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> findReceived(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: reviewee 기준 조회
        return ResponseEntity.ok(ApiResponse.success("REVIEWS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleReview()), page, size, 1, 1, true, true)));
    }

    @GetMapping("/written")
    @Operation(summary = "작성한 리뷰 목록", description = "내가 상대에게 남긴 평가입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> findWritten(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: reviewer 기준 조회
        return ResponseEntity.ok(ApiResponse.success("REVIEWS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleReview()), page, size, 1, 1, true, true)));
    }

    @GetMapping("/summary")
    @Operation(summary = "내 평점 요약", description = "마이페이지의 평균 별점·건수·등급입니다.")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> findSummary(@CurrentAccountId Long accountId) {
        // TODO: 평균/건수 집계 + 등급 산정
        return ResponseEntity.ok(ApiResponse.success("REVIEW_SUMMARY_FOUND", "조회에 성공했습니다.",
                new ReviewSummaryResponse(4.5, 12, "SENIOR")));
    }

    @GetMapping("/pending")
    @Operation(summary = "작성 대기 목록", description = "대금 지급이 끝났는데 아직 리뷰를 쓰지 않은 계약입니다.")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> findPending(@CurrentAccountId Long accountId) {
        // TODO: 종료된 계약 중 미작성 건 조회
        return ResponseEntity.ok(ApiResponse.success("PENDING_REVIEWS_FOUND", "조회에 성공했습니다.",
                List.of(sampleReview())));
    }

    // ==========================================
    // 관리자 (R40)
    // ==========================================

    @GetMapping("/admin/site-reviews/summary")
    @Operation(summary = "[관리자] 사이트 리뷰 요약",
            description = "요약 카드와 별점 분포 그래프에 쓰는 값입니다.")
    public ResponseEntity<ApiResponse<SiteReviewSummaryResponse>> findSiteReviewSummaryForAdmin() {
        // TODO: 평균/건수 집계 + 별점별 count
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_SUMMARY_FOUND", "조회에 성공했습니다.",
                new SiteReviewSummaryResponse(4.8, 4, 1, 2, 2, 3,
                        Map.of(5, 2L, 4, 1L, 3, 1L, 2, 0L, 1, 0L))));
    }

    @GetMapping("/admin/site-reviews")
    @Operation(summary = "[관리자] 사이트 리뷰 목록",
            description = "별점·작성자 구분·공개 여부·홍보 여부로 필터링합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SiteReviewResponse>>> findSiteReviewsForAdmin(
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) PartyRole writerRole,
            @RequestParam(required = false) SiteReviewVisibility visibility,
            @RequestParam(required = false) Boolean promoted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 사이트 리뷰 검색
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEWS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleSiteReview()), page, size, 1, 1, true, true)));
    }

    @PutMapping("/admin/site-reviews/{siteReviewId}/visibility")
    @Operation(summary = "[관리자] 사이트 리뷰 공개·홍보 설정",
            description = "기본값은 비공개입니다. 공개로 바꿔야 메인에 노출될 수 있습니다.")
    public ResponseEntity<ApiResponse<SiteReviewResponse>> updateVisibility(
            @PathVariable Long siteReviewId,
            @Valid @RequestBody SiteReviewVisibilityRequest request
    ) {
        // TODO: 공개 여부·홍보 여부 갱신
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_UPDATED", "설정을 변경했습니다.", sampleSiteReview()));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private ReviewResponse sampleReview() {
        return new ReviewResponse(900L, 600L, "페어링 웹 리뉴얼", "주식회사 페어링",
                PartyRole.CLIENT, 5, "일정 준수가 좋았습니다.", LocalDateTime.now());
    }

    private SiteReviewResponse sampleSiteReview() {
        return new SiteReviewResponse(950L, PartyRole.FREELANCER, "고**", 5,
                "협상이 편했습니다.", "페어링 웹 리뉴얼", SiteReviewVisibility.PRIVATE, false, LocalDateTime.now());
    }
}
