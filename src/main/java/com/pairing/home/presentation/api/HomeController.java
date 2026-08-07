package com.pairing.home.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.home.presentation.api.response.FaqResponse;
import com.pairing.home.presentation.api.response.HomeSummaryResponse;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.domain.model.SiteReviewVisibility;
import com.pairing.review.presentation.api.response.SiteReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 비로그인 메인 페이지. (요구사항 R42)
 *
 * <p>로그인 없이 호출한다. GlobalSecurityConfig 의 permitAll 목록에 /api/v1/home/** 이 있어야 한다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
@Tag(name = "05. Home", description = "비로그인 메인 API")
public class HomeController {

    @GetMapping("/summary")
    @Operation(summary = "메인 지표 조회", description = "완수율·누적 프로젝트·누적 협상 등 메인 상단 숫자입니다.")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> findSummary() {
        // TODO: 집계 쿼리 또는 캐시된 값 반환
        return ResponseEntity.ok(ApiResponse.success("HOME_SUMMARY_FOUND", "조회에 성공했습니다.",
                new HomeSummaryResponse(99.0, 5657L, 6000L, 5000L, 4.8, 300_000_000_000L)));
    }

    @GetMapping("/site-reviews")
    @Operation(summary = "메인 노출 리뷰", description = "관리자가 공개로 설정한 4점 이상 리뷰만 반환합니다. 작성자명은 마스킹됩니다.")
    public ResponseEntity<ApiResponse<List<SiteReviewResponse>>> findSiteReviews(
            @RequestParam(defaultValue = "6") int size
    ) {
        // TODO: 공개 + 4점 이상 조회
        SiteReviewResponse review = new SiteReviewResponse(950L, PartyRole.FREELANCER, "고**", 5,
                "협상이 편했습니다.", null, SiteReviewVisibility.PUBLIC, true, LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success("SITE_REVIEWS_FOUND", "조회에 성공했습니다.", List.of(review)));
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
