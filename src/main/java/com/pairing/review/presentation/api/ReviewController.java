package com.pairing.review.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.application.usecase.ReviewUseCase;
import com.pairing.review.application.usecase.SiteReviewAdminUseCase;
import com.pairing.review.domain.model.SiteReviewVisibility;
import com.pairing.review.exception.ReviewErrorCode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.util.List;

/**
 * 상호 평가와 사이트 후기. (요구사항 R22, R40)
 *
 * <p>대금 지급이 끝난 계약에 대해서만 작성할 수 있고, 한 번 쓰면 수정·삭제할 수 없다.
 * 상대 평가와 사이트 후기를 한 화면에서 작성하므로 등록 API 도 하나다.
 *
 * <p>contract/settlement 도메인이 아직 스켈레톤이라 "완료+지급완료" 자격 검증은 하지 않는다.
 * {@code findPending} 은 그 데이터가 없어 항상 빈 리스트를 반환한다(TODO).
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "16. Review", description = "평점/리뷰 API")
public class ReviewController {

    private final ReviewUseCase reviewUseCase;
    private final SiteReviewAdminUseCase siteReviewAdminUseCase;

    @PostMapping
    @Operation(summary = "리뷰 작성",
            description = "상대 평가(필수)와 사이트 후기(선택)를 함께 등록합니다. 작성 후 수정·삭제할 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = ReviewErrorCode.class, value = {"ALREADY_REVIEWED"})
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody ReviewCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        ReviewResponse response = ReviewResponse.from(reviewUseCase.create(request.toCommand(accountId)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("REVIEW_CREATED", "리뷰를 등록했습니다.", response));
    }

    @GetMapping("/received")
    @Operation(summary = "받은 리뷰 목록", description = "상대가 나에게 남긴 평가입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> findReceived(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ReviewResponse> response = PageResponse.from(
                reviewUseCase.findReceived(accountId, pageable).map(ReviewResponse::from));
        return ResponseEntity.ok(ApiResponse.success("REVIEWS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/written")
    @Operation(summary = "작성한 리뷰 목록", description = "내가 상대에게 남긴 평가입니다.")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> findWritten(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ReviewResponse> response = PageResponse.from(
                reviewUseCase.findWritten(accountId, pageable).map(ReviewResponse::from));
        return ResponseEntity.ok(ApiResponse.success("REVIEWS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/summary")
    @Operation(summary = "내 평점 요약", description = "마이페이지의 평균 별점·건수·등급입니다.")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> findSummary(@CurrentAccountId Long accountId) {
        ReviewSummaryResponse response = ReviewSummaryResponse.from(reviewUseCase.getSummary(accountId));
        return ResponseEntity.ok(ApiResponse.success("REVIEW_SUMMARY_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/pending")
    @Operation(summary = "작성 대기 목록",
            description = "대금 지급이 끝났는데 아직 리뷰를 쓰지 않은 계약입니다. "
                    + "contract/settlement 도메인 구현 전까지는 항상 빈 목록입니다.")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> findPending(@CurrentAccountId Long accountId) {
        List<ReviewResponse> response = reviewUseCase.findPending(accountId).stream()
                .map(ReviewResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("PENDING_REVIEWS_FOUND", "조회에 성공했습니다.", response));
    }

    // ==========================================
    // 관리자 (R40)
    // ==========================================

    @GetMapping("/admin/site-reviews/summary")
    @Operation(summary = "[관리자] 사이트 리뷰 요약",
            description = "요약 카드와 별점 분포 그래프에 쓰는 값입니다.")
    public ResponseEntity<ApiResponse<SiteReviewSummaryResponse>> findSiteReviewSummaryForAdmin() {
        SiteReviewSummaryResponse response = SiteReviewSummaryResponse.from(siteReviewAdminUseCase.getSummary());
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_SUMMARY_FOUND", "조회에 성공했습니다.", response));
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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<SiteReviewResponse> response = PageResponse.from(
                siteReviewAdminUseCase.search(score, writerRole, visibility, promoted, pageable)
                        .map(SiteReviewResponse::from));
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEWS_FOUND", "조회에 성공했습니다.", response));
    }

    @PutMapping("/admin/site-reviews/{siteReviewId}/visibility")
    @Operation(summary = "[관리자] 사이트 리뷰 공개·홍보 설정",
            description = "기본값은 비공개입니다. 공개로 바꿔야 메인에 노출될 수 있습니다.")
    @ApiErrorCodeExample(domain = ReviewErrorCode.class, value = {"SITE_REVIEW_NOT_FOUND"})
    public ResponseEntity<ApiResponse<SiteReviewResponse>> updateVisibility(
            @PathVariable Long siteReviewId,
            @Valid @RequestBody SiteReviewVisibilityRequest request
    ) {
        SiteReviewResponse response = SiteReviewResponse.from(siteReviewAdminUseCase.updateVisibility(
                siteReviewId, request.visibility(), request.promoted()));
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEW_UPDATED", "설정을 변경했습니다.", response));
    }
}
