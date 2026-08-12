package com.pairing.home.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.home.presentation.api.response.FaqResponse;
import com.pairing.home.presentation.api.response.HomeSummaryResponse;
import com.pairing.review.application.usecase.SiteReviewPublicUseCase;
import com.pairing.review.presentation.api.response.SiteReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 비로그인 메인 페이지. (요구사항 R42)
 *
 * <p>로그인 없이 호출한다. GlobalSecurityConfig 의 permitAll 목록에 /api/v1/home/** 이 있어야 한다.
 *
 * <p>{@code /site-reviews} 는 리뷰 도메인에 실제로 연결되어 있다. {@code /summary}/{@code /faqs} 는
 * project/negotiation 등 다른 도메인 집계가 필요해서 스켈레톤 고정 응답을 유지한다.
 */
@RestController
@Validated
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
@Tag(name = "05. Home", description = "비로그인 메인 API")
public class HomeController {

    /** 메인 후기 슬라이더가 한 번에 보여주는 개수의 상한. 화면이 이보다 많이 필요해질 일이 없다. */
    private static final int MAX_HOME_REVIEW_SIZE = 20;

    private final SiteReviewPublicUseCase siteReviewPublicUseCase;

    @GetMapping("/summary")
    @Operation(summary = "메인 지표 조회", description = "완수율·누적 프로젝트·누적 협상 등 메인 상단 숫자입니다.")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> findSummary() {
        // TODO: 집계 쿼리 또는 캐시된 값 반환
        return ResponseEntity.ok(ApiResponse.success("HOME_SUMMARY_FOUND", "조회에 성공했습니다.",
                new HomeSummaryResponse(99.0, 5657L, 6000L, 5000L, 4.8, 300_000_000_000L)));
    }

    @GetMapping("/site-reviews")
    @Operation(summary = "메인 노출 리뷰",
            description = "관리자가 공개+홍보 활용으로 설정한 4점 이상 리뷰만 반환합니다. "
                    + "작성자명은 마스킹됩니다. size 는 1~20 입니다.")
    public ResponseEntity<ApiResponse<List<SiteReviewResponse>>> findSiteReviews(
            // 비로그인 API 라 아무나 부를 수 있다. 상한이 없으면 size 를 크게 넣어 홍보 리뷰를
            // 통째로 긁어갈 수 있고, 리뷰마다 프로젝트·계정을 읽으므로 조회가 size 만큼 늘어난다.
            @RequestParam(defaultValue = "6") @Min(1) @Max(MAX_HOME_REVIEW_SIZE) int size
    ) {
        List<SiteReviewResponse> response = siteReviewPublicUseCase.findPromoted(size).stream()
                .map(SiteReviewResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEWS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/faqs")
    @Operation(summary = "자주 찾는 질문")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> findFaqs() {
        // TODO: FAQ 목록 조회 (초기에는 고정 문구)
        return ResponseEntity.ok(ApiResponse.success("FAQS_FOUND", "조회에 성공했습니다.",
                List.of(new FaqResponse("페어링의 매칭 프로세스가 궁금합니다",
                        "프로젝트 등록 → 프리랜서 매칭 → 협상 → 계약 성사 순으로 진행됩니다.", 1))));
    }
}
