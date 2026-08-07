package com.pairing.review.presentation.api.request;

import com.pairing.review.domain.model.SiteReviewVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** [관리자] 사이트 리뷰 공개·홍보 설정. (요구사항 R40) */
@Schema(description = "사이트 리뷰 노출 설정 요청")
public record SiteReviewVisibilityRequest(

        @Schema(description = "공개 여부", example = "PUBLIC")
        @NotNull(message = "공개 여부는 필수입니다.")
        SiteReviewVisibility visibility,

        @Schema(description = "홍보 활용 여부", example = "true")
        @NotNull(message = "홍보 활용 여부는 필수입니다.")
        Boolean promoted
) {
}
